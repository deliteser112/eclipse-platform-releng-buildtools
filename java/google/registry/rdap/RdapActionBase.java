// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.rdap;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.request.Actions.getPathForAction;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.re2j.Pattern;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.cmd.Query;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.EppResource;
import google.registry.model.registrar.Registrar;
import google.registry.rdap.RdapMetrics.EndpointType;
import google.registry.rdap.RdapMetrics.WildcardType;
import google.registry.rdap.RdapObjectClasses.ErrorResponse;
import google.registry.rdap.RdapObjectClasses.ReplyPayloadBase;
import google.registry.rdap.RdapObjectClasses.TopLevelReplyObject;
import google.registry.rdap.RdapSearchResults.BaseSearchResponse;
import google.registry.rdap.RdapSearchResults.IncompletenessWarningType;
import google.registry.request.Action;
import google.registry.request.HttpException;
import google.registry.request.HttpException.UnprocessableEntityException;
import google.registry.request.Parameter;
import google.registry.request.RequestMethod;
import google.registry.request.RequestPath;
import google.registry.request.Response;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.AuthenticatedRegistrarAccessor;
import google.registry.request.auth.UserAuthInfo;
import google.registry.util.Clock;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.DateTime;

/**
 * Base RDAP (new WHOIS) action for all requests.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 */
public abstract class RdapActionBase implements Runnable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Pattern for checking LDH names, which must officially contains only alphanumeric plus dots and
   * hyphens. In this case, allow the wildcard asterisk as well.
   */
  static final Pattern LDH_PATTERN = Pattern.compile("[-.a-zA-Z0-9*]+");
  private static final int RESULT_SET_SIZE_SCALING_FACTOR = 30;

  private static final MediaType RESPONSE_MEDIA_TYPE =
      MediaType.create("application", "rdap+json").withCharset(UTF_8);

  /** Whether to include or exclude deleted items from a query. */
  protected enum DeletedItemHandling {
    EXCLUDE,
    INCLUDE
  }

  @Inject Response response;
  @Inject Clock clock;
  @Inject @RequestMethod Action.Method requestMethod;
  @Inject @RequestPath String requestPath;
  @Inject AuthResult authResult;
  @Inject AuthenticatedRegistrarAccessor registrarAccessor;
  @Inject RdapJsonFormatter rdapJsonFormatter;
  @Inject @Parameter("registrar") Optional<String> registrarParam;
  @Inject @Parameter("includeDeleted") Optional<Boolean> includeDeletedParam;
  @Inject @Parameter("formatOutput") Optional<Boolean> formatOutputParam;
  @Inject @Config("rdapWhoisServer") @Nullable String rdapWhoisServer;
  @Inject @Config("rdapResultSetMaxSize") int rdapResultSetMaxSize;
  @Inject RdapMetrics rdapMetrics;

  /** Builder for metric recording. */
  final RdapMetrics.RdapMetricInformation.Builder metricInformationBuilder =
      RdapMetrics.RdapMetricInformation.builder();

  private final String humanReadableObjectTypeName;

  /** Returns a string like "domain name" or "nameserver", used for error strings. */
  final String getHumanReadableObjectTypeName() {
    return humanReadableObjectTypeName;
  }

  /** The endpoint type used for recording metrics. */
  private final EndpointType endpointType;

  /** Returns the servlet action path; used to extract the search string from the incoming path. */
  final String getActionPath() {
    return getPathForAction(getClass());
  }

  RdapActionBase(String humanReadableObjectTypeName, EndpointType endpointType) {
    this.humanReadableObjectTypeName = humanReadableObjectTypeName;
    this.endpointType = endpointType;
  }

  /**
   * Does the actual search and returns an RDAP JSON object.
   *
   * @param pathSearchString the search string in the URL path
   * @param isHeadRequest whether the returned map will actually be used. HTTP HEAD requests don't
   *        actually return anything. However, we usually still want to go through the process of
   *        building a map, to make sure that the request would return a 500 status if it were
   *        invoked using GET. So this field should usually be ignored, unless there's some
   *        expensive task required to create the map which will never result in a request failure.
   * @return A map (probably containing nested maps and lists) with the final JSON response data.
   */
  abstract ReplyPayloadBase getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest);

  @Override
  public void run() {
    metricInformationBuilder.setIncludeDeleted(includeDeletedParam.orElse(false));
    metricInformationBuilder.setRegistrarSpecified(registrarParam.isPresent());
    metricInformationBuilder.setRole(getAuthorization().role());
    metricInformationBuilder.setRequestMethod(requestMethod);
    metricInformationBuilder.setEndpointType(endpointType);
    try {
      // Extract what we're searching for from the request path. Some RDAP commands use trailing
      // data in the path itself (e.g. /rdap/domain/mydomain.com), and some use the query string
      // (e.g. /rdap/domains?name=mydomain); the query parameters are extracted by the subclasses
      // directly as needed.
      response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      URI uri = new URI(requestPath);
      String pathProper = uri.getPath();
      checkArgument(
          pathProper.startsWith(getActionPath()),
          "%s doesn't start with %s", pathProper, getActionPath());
      ReplyPayloadBase replyObject =
          getJsonObjectForResource(
              pathProper.substring(getActionPath().length()), requestMethod == Action.Method.HEAD);
      if (replyObject instanceof BaseSearchResponse) {
        metricInformationBuilder.setIncompletenessWarningType(
            ((BaseSearchResponse) replyObject).incompletenessWarningType());
      }
      response.setStatus(SC_OK);
      response.setContentType(RESPONSE_MEDIA_TYPE);
      setPayload(replyObject);
      metricInformationBuilder.setStatusCode(SC_OK);
    } catch (HttpException e) {
      setError(e.getResponseCode(), e.getResponseCodeString(), e.getMessage());
    } catch (URISyntaxException | IllegalArgumentException e) {
      setError(SC_BAD_REQUEST, "Bad Request", "Not a valid " + getHumanReadableObjectTypeName());
    } catch (RuntimeException e) {
      setError(SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "An error was encountered");
      logger.atSevere().withCause(e).log("Exception encountered while processing RDAP command");
    }
    rdapMetrics.updateMetrics(metricInformationBuilder.build());
  }

  void setError(int status, String title, String description) {
    metricInformationBuilder.setStatusCode(status);
    response.setStatus(status);
    response.setContentType(RESPONSE_MEDIA_TYPE);
    try {
      setPayload(ErrorResponse.create(status, title, description));
    } catch (Exception ex) {
      logger.atSevere().withCause(ex).log("Failed to create an error response.");
      response.setPayload("");
    }
  }

  void setPayload(ReplyPayloadBase replyObject) {
    if (requestMethod == Action.Method.HEAD) {
      return;
    }

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.disableHtmlEscaping();
    if (formatOutputParam.orElse(false)) {
      gsonBuilder.setPrettyPrinting();
    }
    Gson gson = gsonBuilder.create();

    TopLevelReplyObject topLevelObject =
        TopLevelReplyObject.create(replyObject, rdapJsonFormatter.createTosNotice());

    response.setPayload(gson.toJson(topLevelObject.toJson()));
  }

  RdapAuthorization getAuthorization() {
    if (!authResult.userAuthInfo().isPresent()) {
      return RdapAuthorization.PUBLIC_AUTHORIZATION;
    }
    UserAuthInfo userAuthInfo = authResult.userAuthInfo().get();
    if (userAuthInfo.isUserAdmin()) {
      return RdapAuthorization.ADMINISTRATOR_AUTHORIZATION;
    }
    ImmutableSet<String> clientIds = registrarAccessor.getAllClientIdWithRoles().keySet();
    if (clientIds.isEmpty()) {
      logger.atWarning().log("Couldn't find registrar for User %s.", authResult.userIdForLogging());
      return RdapAuthorization.PUBLIC_AUTHORIZATION;
    }
    return RdapAuthorization.create(RdapAuthorization.Role.REGISTRAR, clientIds);
  }

  /** Returns the registrar on which results should be filtered, or absent(). */
  Optional<String> getDesiredRegistrar() {
    return registrarParam;
  }

  /**
   * Returns true if the query should include deleted items.
   *
   * <p>This is true only if the request specified an includeDeleted parameter of true, AND is
   * eligible to see deleted information. Admins can see all deleted information, while
   * authenticated registrars can see only their own deleted information. Note that if this method
   * returns true, it just means that some deleted information might be viewable. If this is a
   * registrar request, the caller must still verify that the registrar can see each particular
   * item by calling {@link RdapAuthorization#isAuthorizedForClientId}.
   */
  boolean shouldIncludeDeleted() {
    // If includeDeleted is not specified, or set to false, we don't need to go any further.
    if (!includeDeletedParam.orElse(false)) {
      return false;
    }
    if (!authResult.userAuthInfo().isPresent()) {
      return false;
    }
    UserAuthInfo userAuthInfo = authResult.userAuthInfo().get();
    if (userAuthInfo.isUserAdmin()) {
      return true;
    }
    return !registrarAccessor.getAllClientIdWithRoles().isEmpty();
  }

  DeletedItemHandling getDeletedItemHandling() {
    return shouldIncludeDeleted() ? DeletedItemHandling.INCLUDE : DeletedItemHandling.EXCLUDE;
  }

  /**
   * Returns true if the request is authorized to see the resource.
   *
   * <p>This is true if the resource is not deleted, or the request wants to see deleted items, and
   * is authorized to do so.
   */
  boolean isAuthorized(EppResource eppResource, DateTime now) {
    return now.isBefore(eppResource.getDeletionTime())
            || (shouldIncludeDeleted()
                && getAuthorization()
                    .isAuthorizedForClientId(eppResource.getPersistedCurrentSponsorClientId()));
  }

  /**
   * Returns true if the EPP resource should be visible.
   *
   * <p>This is true iff:
   * 1. The resource is not deleted, or the request wants to see deleted items, and is authorized to
   *    do so, and:
   * 2. The request did not specify a registrar to filter on, or the registrar matches.
   */
  boolean shouldBeVisible(EppResource eppResource, DateTime now) {
    return isAuthorized(eppResource, now)
        && (!registrarParam.isPresent()
            || registrarParam.get().equals(eppResource.getPersistedCurrentSponsorClientId()));
  }

  /**
   * Returns true if the EPP resource should be visible.
   *
   * <p>This is true iff:
   * 1. The passed in resource exists and is not deleted (deleted ones will have been projected
   *    forward in time to empty),
   * 2. The request did not specify a registrar to filter on, or the registrar matches.
   */
  boolean shouldBeVisible(Optional<? extends EppResource> eppResource, DateTime now) {
    return eppResource.isPresent() && shouldBeVisible(eppResource.get(), now);
  }

  /**
   * Returns true if the registrar should be visible.
   *
   * <p>This is true iff:
   * 1. The resource is active and publicly visible, or the request wants to see deleted items, and
   *     is authorized to do so, and:
   * 2. The request did not specify a registrar to filter on, or the registrar matches.
   */
  boolean shouldBeVisible(Registrar registrar) {
    return (registrar.isLiveAndPubliclyVisible()
            || (shouldIncludeDeleted()
                && getAuthorization().isAuthorizedForClientId(registrar.getClientId())))
        && (!registrarParam.isPresent() || registrarParam.get().equals(registrar.getClientId()));
  }

  String canonicalizeName(String name) {
    name = canonicalizeDomainName(name);
    if (name.endsWith(".")) {
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }

  int getStandardQuerySizeLimit() {
    return shouldIncludeDeleted()
            ? (RESULT_SET_SIZE_SCALING_FACTOR * (rdapResultSetMaxSize + 1))
            : (rdapResultSetMaxSize + 1);
  }

  static <T extends EppResource> Query<T> queryItems(
      Class<T> clazz,
      String filterField,
      RdapSearchPattern partialStringQuery,
      DeletedItemHandling deletedItemHandling,
      int resultSetMaxSize) {
    return queryItems(
        clazz,
        filterField,
        partialStringQuery,
        Optional.empty(),
        deletedItemHandling,
        resultSetMaxSize);
  }

  /**
   * Handles prefix searches in cases where, if we need to filter out deleted items, there are no
   * pending deletes.
   *
   * <p>In such cases, it is sufficient to check whether {@code deletionTime} is equal to
   * {@code END_OF_TIME}, because any other value means it has already been deleted. This allows us
   * to use an equality query for the deletion time.
   *
   * @param clazz the type of resource to be queried
   * @param filterField the database field of interest
   * @param partialStringQuery the details of the search string; if there is no wildcard, an
   *        equality query is used; if there is a wildcard, a range query is used instead; the
   *        initial string should not be empty, and any search suffix will be ignored, so the caller
   *        must filter the results if a suffix is specified
   * @param cursorString if a cursor is present, this parameter should specify the cursor string, to
   *        skip any results up to and including the string; empty() if there is no cursor
   * @param deletedItemHandling whether to include or exclude deleted items
   * @param resultSetMaxSize the maximum number of results to return
   * @return the query object
   */
  static <T extends EppResource> Query<T> queryItems(
      Class<T> clazz,
      String filterField,
      RdapSearchPattern partialStringQuery,
      Optional<String> cursorString,
      DeletedItemHandling deletedItemHandling,
      int resultSetMaxSize) {
    if (partialStringQuery.getInitialString().length()
        < RdapSearchPattern.MIN_INITIAL_STRING_LENGTH) {
      throw new UnprocessableEntityException(
          String.format(
              "Initial search string must be at least %d characters",
              RdapSearchPattern.MIN_INITIAL_STRING_LENGTH));
    }
    Query<T> query = ofy().load().type(clazz);
    if (!partialStringQuery.getHasWildcard()) {
      query = query.filter(filterField, partialStringQuery.getInitialString());
    } else {
      // Ignore the suffix; the caller will need to filter on the suffix, if any.
      query = query
          .filter(filterField + " >=", partialStringQuery.getInitialString())
          .filter(filterField + " <", partialStringQuery.getNextInitialString());
    }
    if (cursorString.isPresent()) {
      query = query.filter(filterField + " >", cursorString.get());
    }
    return setOtherQueryAttributes(query, deletedItemHandling, resultSetMaxSize);
  }

  /**
   * Handles searches using a simple string rather than an {@link RdapSearchPattern}.
   *
   * <p>Since the filter is not an inequality, we can support also checking a cursor string against
   * a different field (which involves an inequality on that field).
   *
   * @param clazz the type of resource to be queried
   * @param filterField the database field of interest
   * @param queryString the search string
   * @param cursorField the field which should be compared to the cursor string, or empty() if the
   *        key should be compared to a key created from the cursor string
   * @param cursorString if a cursor is present, this parameter should specify the cursor string, to
   *        skip any results up to and including the string; empty() if there is no cursor
   * @param deletedItemHandling whether to include or exclude deleted items
   * @param resultSetMaxSize the maximum number of results to return
   * @return the query object
   */
  static <T extends EppResource> Query<T> queryItems(
      Class<T> clazz,
      String filterField,
      String queryString,
      Optional<String> cursorField,
      Optional<String> cursorString,
      DeletedItemHandling deletedItemHandling,
      int resultSetMaxSize) {
    if (queryString.length() < RdapSearchPattern.MIN_INITIAL_STRING_LENGTH) {
      throw new UnprocessableEntityException(
          String.format(
              "Initial search string must be at least %d characters",
              RdapSearchPattern.MIN_INITIAL_STRING_LENGTH));
    }
    Query<T> query = ofy().load().type(clazz).filter(filterField, queryString);
    if (cursorString.isPresent()) {
      if (cursorField.isPresent()) {
        query = query.filter(cursorField.get() + " >", cursorString.get());
      } else {
        query = query.filterKey(">", Key.create(clazz, cursorString.get()));
      }
    }
    return setOtherQueryAttributes(query, deletedItemHandling, resultSetMaxSize);
  }

  /** Handles searches where the field to be searched is the key. */
  static <T extends EppResource> Query<T> queryItemsByKey(
      Class<T> clazz,
      RdapSearchPattern partialStringQuery,
      Optional<String> cursorString,
      DeletedItemHandling deletedItemHandling,
      int resultSetMaxSize) {
    if (partialStringQuery.getInitialString().length()
        < RdapSearchPattern.MIN_INITIAL_STRING_LENGTH) {
      throw new UnprocessableEntityException(
          String.format(
              "Initial search string must be at least %d characters",
              RdapSearchPattern.MIN_INITIAL_STRING_LENGTH));
    }
    Query<T> query = ofy().load().type(clazz);
    if (!partialStringQuery.getHasWildcard()) {
      query = query.filterKey("=", Key.create(clazz, partialStringQuery.getInitialString()));
    } else {
      // Ignore the suffix; the caller will need to filter on the suffix, if any.
      query = query
          .filterKey(">=", Key.create(clazz, partialStringQuery.getInitialString()))
          .filterKey("<", Key.create(clazz, partialStringQuery.getNextInitialString()));
    }
    if (cursorString.isPresent()) {
      query = query.filterKey(">", Key.create(clazz, cursorString.get()));
    }
    return setOtherQueryAttributes(query, deletedItemHandling, resultSetMaxSize);
  }

  /** Handles searches by key using a simple string. */
  static <T extends EppResource> Query<T> queryItemsByKey(
      Class<T> clazz,
      String queryString,
      DeletedItemHandling deletedItemHandling,
      int resultSetMaxSize) {
    if (queryString.length() < RdapSearchPattern.MIN_INITIAL_STRING_LENGTH) {
      throw new UnprocessableEntityException(
          String.format(
              "Initial search string must be at least %d characters",
              RdapSearchPattern.MIN_INITIAL_STRING_LENGTH));
    }
    Query<T> query = ofy().load().type(clazz).filterKey("=", Key.create(clazz, queryString));
    return setOtherQueryAttributes(query, deletedItemHandling, resultSetMaxSize);
  }

  private static <T extends EppResource> Query<T> setOtherQueryAttributes(
      Query<T> query, DeletedItemHandling deletedItemHandling, int resultSetMaxSize) {
    if (deletedItemHandling != DeletedItemHandling.INCLUDE) {
      query = query.filter("deletionTime", END_OF_TIME);
    }
    return query.limit(resultSetMaxSize);
  }

  /**
   * Runs the given query, and checks for permissioning if necessary.
   *
   * @param query an already-defined query to be run; a filter on currentSponsorClientId will be
   *        added if appropriate
   * @param now the time as of which to evaluate the query
   * @param checkForVisibility true if the results should be checked to make sure they are visible;
   *        normally this should be equal to the shouldIncludeDeleted setting, but in cases where
   *        the query could not check deletion status (due to Datastore limitations such as the
   *        limit of one field queried for inequality, for instance), it may need to be set to true
   *        even when not including deleted records
   * @param querySizeLimit the maximum number of items the query is expected to return, usually
   *        because the limit has been set
   * @return an {@link RdapResultSet} object containing the list of
   *         resources and an incompleteness warning flag, which is set to MIGHT_BE_INCOMPLETE iff
   *         any resources were excluded due to lack of visibility, and the resulting list of
   *         resources is less than the maximum allowable, and the number of items returned by the
   *         query is greater than or equal to the maximum number we might have expected
   */
  <T extends EppResource> RdapResultSet<T> getMatchingResources(
      Query<T> query, boolean checkForVisibility, DateTime now, int querySizeLimit) {
    Optional<String> desiredRegistrar = getDesiredRegistrar();
    if (desiredRegistrar.isPresent()) {
      query = query.filter("currentSponsorClientId", desiredRegistrar.get());
    }
    if (!checkForVisibility) {
      return RdapResultSet.create(query.list());
    }
    // If we are including deleted resources, we need to check that we're authorized for each one.
    List<T> resources = new ArrayList<>();
    int numResourcesQueried = 0;
    boolean someExcluded = false;
    for (T resource : query) {
      if (shouldBeVisible(resource, now)) {
        resources.add(resource);
      } else {
        someExcluded = true;
      }
      numResourcesQueried++;
      if (resources.size() > rdapResultSetMaxSize) {
        break;
      }
    }
    // The incompleteness problem comes about because we don't know how many items to fetch. We want
    // to return rdapResultSetMaxSize worth of items, but some might be excluded, so we fetch more
    // just in case. But how many more? That's the potential problem, addressed with the three way
    // AND statement:
    // 1. If we didn't exclude any items, then we can't have the incompleteness problem.
    // 2. If have a full result set batch (rdapResultSetMaxSize items), we must by definition be
    //    giving the user a complete result set.
    // 3. If we started with fewer than querySizeLimit items, then there weren't any more items that
    //    we missed. Even if we return fewer than rdapResultSetMaxSize items, it isn't because we
    // didn't fetch enough to start.
    // Only if all three conditions are true might things be incomplete. In other words, we fetched
    // as many as our limit allowed, but then excluded so many that we wound up with less than a
    // full result set's worth of results.
    return RdapResultSet.create(
        resources,
        (someExcluded
                && (resources.size() < rdapResultSetMaxSize)
                && (numResourcesQueried >= querySizeLimit))
            ? IncompletenessWarningType.MIGHT_BE_INCOMPLETE
            : IncompletenessWarningType.COMPLETE,
        numResourcesQueried);
  }

  RdapSearchPattern recordWildcardType(RdapSearchPattern partialStringQuery) {
    if (!partialStringQuery.getHasWildcard()) {
      metricInformationBuilder.setWildcardType(WildcardType.NO_WILDCARD);
    } else if (partialStringQuery.getSuffix() == null) {
      metricInformationBuilder.setWildcardType(WildcardType.PREFIX);
    } else if (partialStringQuery.getInitialString().isEmpty()) {
      metricInformationBuilder.setWildcardType(WildcardType.SUFFIX);
    } else {
      metricInformationBuilder.setWildcardType(WildcardType.PREFIX_AND_SUFFIX);
    }
    metricInformationBuilder.setPrefixLength(partialStringQuery.getInitialString().length());
    return partialStringQuery;
  }
}
