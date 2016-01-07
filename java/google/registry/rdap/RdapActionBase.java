// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.registry.Registries.findTldForName;
import static google.registry.model.registry.Registries.getTlds;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InternetDomainName;
import com.google.common.net.MediaType;
import com.google.re2j.Pattern;
import com.googlecode.objectify.cmd.Query;
import google.registry.config.ConfigModule.Config;
import google.registry.model.EppResource;
import google.registry.request.Action;
import google.registry.request.HttpException;
import google.registry.request.HttpException.BadRequestException;
import google.registry.request.HttpException.NotFoundException;
import google.registry.request.RequestMethod;
import google.registry.request.RequestPath;
import google.registry.request.Response;
import google.registry.util.FormattingLogger;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.json.simple.JSONValue;

/**
 * Base RDAP (new WHOIS) action for single-item domain, nameserver and entity requests.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 */
public abstract class RdapActionBase implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * Pattern for checking LDH names, which must officially contains only alphanumeric plus dots and
   * hyphens. In this case, allow the wildcard asterisk as well.
   */
  static final Pattern LDH_PATTERN = Pattern.compile("[-.a-zA-Z0-9*]+");

  private static final MediaType RESPONSE_MEDIA_TYPE = MediaType.create("application", "rdap+json");

  @Inject Response response;
  @Inject @RequestMethod Action.Method requestMethod;
  @Inject @RequestPath String requestPath;
  @Inject @Config("rdapLinkBase") String rdapLinkBase;
  @Inject @Config("rdapWhoisServer") @Nullable String rdapWhoisServer;

  /** Returns a string like "domain name" or "nameserver", used for error strings. */
  abstract String getHumanReadableObjectTypeName();

  /** Returns the servlet action path; used to extract the search string from the incoming path. */
  abstract String getActionPath();

  /**
   * Does the actual search and returns an RDAP JSON object.
   *
   * @param pathSearchString the search string in the URL path
   * @param isHeadRequest whether the returned map will actually be used. HTTP HEAD requests don't
   *        actually return anything. However, we usually still want to go through the process of
   *        building a map, to make sure that the request would return a 500 status if it were
   *        invoked using GET. So this field should usually be ignored, unless there's some
   *        expensive task required to create the map which will never result in a request failure.
   * @param linkBase the base URL for RDAP link structures
   * @return A map (probably containing nested maps and lists) with the final JSON response data.
   */
  abstract ImmutableMap<String, Object> getJsonObjectForResource(
      String pathSearchString, boolean isHeadRequest, String linkBase);

  @Override
  public void run() {
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
      ImmutableMap<String, Object> rdapJson =
          getJsonObjectForResource(
              pathProper.substring(getActionPath().length()),
              requestMethod == Action.Method.HEAD,
              rdapLinkBase);
      response.setStatus(SC_OK);
      if (requestMethod != Action.Method.HEAD) {
        response.setPayload(JSONValue.toJSONString(rdapJson));
      }
      response.setContentType(MediaType.create("application", "rdap+json"));
    } catch (HttpException e) {
      setError(e.getResponseCode(), e.getResponseCodeString(), e.getMessage());
    } catch (URISyntaxException | IllegalArgumentException e) {
      setError(SC_BAD_REQUEST, "Bad Request", "Not a valid " + getHumanReadableObjectTypeName());
    } catch (RuntimeException e) {
      setError(SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "An error was encountered");
      logger.severe(e, "Exception encountered while processing RDAP command");
    }
  }

  void setError(int status, String title, String description) {
    response.setStatus(status);
    try {
      if (requestMethod != Action.Method.HEAD) {
        response.setPayload(
            JSONValue.toJSONString(RdapJsonFormatter.makeError(status, title, description)));
      }
      response.setContentType(RESPONSE_MEDIA_TYPE);
    } catch (Exception ex) {
      if (requestMethod != Action.Method.HEAD) {
        response.setPayload("");
      }
    }
  }

  void validateDomainName(String name) {
    try {
      Optional<InternetDomainName> tld = findTldForName(InternetDomainName.from(name));
      if (!tld.isPresent() || !getTlds().contains(tld.get().toString())) {
        throw new NotFoundException(name + " not found");
      }
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          name + " is not a valid " + getHumanReadableObjectTypeName());
    }
  }

  String canonicalizeName(String name) {
    name = canonicalizeDomainName(name);
    if (name.endsWith(".")) {
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }

  /**
   * Handles prefix searches in cases where there are no pending deletes. In such cases, it is
   * sufficient to check whether {@code deletionTime} is equal to {@code END_OF_TIME}, because any
   * other value means it has already been deleted. This allows us to use an equality query for the
   * deletion time.
   *
   * @param clazz the type of resource to be queried
   * @param filterField the database field of interest
   * @param partialStringQuery the details of the search string; if there is no wildcard, an
   *        equality query is used; if there is a wildcard, a range query is used instead; there
   *        should not be a search suffix
   * @param resultSetMaxSize the maximum number of results to return
   * @return the results of the query
   */
  static <T extends EppResource> Query<T> queryUndeleted(
      Class<T> clazz,
      String filterField,
      RdapSearchPattern partialStringQuery,
      int resultSetMaxSize) {
    if (!partialStringQuery.getHasWildcard()) {
      return ofy().load()
          .type(clazz)
          .filter(filterField, partialStringQuery.getInitialString())
          .filter("deletionTime", END_OF_TIME)
          .limit(resultSetMaxSize);
    } else {
      checkArgument(partialStringQuery.getSuffix() == null, "Unexpected search string suffix");
      return ofy().load()
          .type(clazz)
          .filter(filterField + " >=", partialStringQuery.getInitialString())
          .filter(filterField + " <", partialStringQuery.getNextInitialString())
          .filter("deletionTime", END_OF_TIME)
          .limit(resultSetMaxSize);
    }
  }
}
