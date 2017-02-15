
# Adding Client Secrets

To use the nomulus tool to administer a nomulus instance, you will need to
obtain OAuth client ids for each of your environment.  There's no reason you
can't use the same client id for all of your environments.

To obtain a client id, go to your project's ["credentials"
page](https://console.developers.google.com/apis/credentials) in the Developer's
Console.  Click "Create credentials" and select "OAuth client Id" from the
dropdown.  In the create credentials window, select an application type of
"Other."

When you return to the main credentials page, click the download icon to the
right of the client id that you just created.  This will download a json file
that you should copy to this directory for all of the environments that you
want to use.  Don't copy over the "UNITTEST" secret, otherwise your unit tests
will break.
