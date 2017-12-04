This library includes custom serializers for AppEngine classes in
com.google.appengine.api.* packages.  This is necessary because serializers
are discovered by AppEngine using a naming pattern that requires that the
data and serializer classes be in the same package (similar to how the Java
Beans introspector finds BeanInfo classes).

In Objectify versions 4.1 and later, the GWT emulation classes were broken
out into a separate versioned jar. Since we are jarjar repackaging the core
Objectify library to include a version number in the package, we need to
include the GWT files in this folder and apply the same changes to them.

The specific patches are:

* Fix ofy().load().fromEntity(...) to respect @OnLoad callbacks.
* Add Session.getKeys() to enumerate everything read in a session.

These changes are already in upstream, but no 4.x release has been made
that incorporates them. Therefore we need to backport them and vendor the
Objectify libarary here.
