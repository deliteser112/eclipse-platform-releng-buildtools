# JSch

JSch is a library for making SSH and SFTP connections from Java. It is released
under a BSD-style license. For more details, see its [project
page](http://www.jcraft.com/jsch/).

## Local modifications by Google

Added three new features and one backport. Default behavior is preserved if they
aren't used.

### Global ThreadFactory instance

This allows the library to be used on Google App Engine (GAE), which doesn't
allow apps to call `new Thread()`. To do this, you must override
`JSch.threadFactory` with something from GAE's ThreadManager. Another global is
also provided to disable calls to `Thread#setName` which always crash on GAE,
regardless of ThreadFactory.

**Files edited:**

*   `JSch.java` - Defined new `threadFactory` and `useThreadNames` fields.
*   `Util.java` - Updated thread creation code.
*   `ChannelDirectTCPIP.java` - Updated thread creation code.
*   `ChannelSubsystem.java` - Updated thread creation code.
*   `ChannelShell.java` - Updated thread creation code.
*   `Session.java` - Updated thread creation code.
*   `ChannelForwardedTCPIP.java` - Updated thread creation code.
*   `ChannelExec.java` - Updated thread creation code.

### Multiple pending requests with SFTP

This is disabled by default. Call `SftpChannel.setMaxPendingRequests(n)` with
n>1 to enable (64 is a reasonable value). Can provide speed improvements of
10-20x if Periscope is disabled, and approximately 7x if Periscope is enabled
and write flushing is disabled.

**Files edited:**

*   `ChannelSftp.java` - Added alternate methods (fastGet, fastRead) that use
    new algorithm. If feature is enabled, the local window size will be set to
    Integer.MAX_VALUE, since flow control is already handled by SFTP, and some
    servers don't work well with smaller windows.

### Allow disabling flushing of writes

Flushing is enabled by default. Call Session.setFlushingEnabled(false) before
connecting to disable. Due to Periscope latency, flushing must be disabled to
take advantage of multiple pending requests.

**Files edited:**

*   `Session.java` - Added simple getter/setter for flushingEnabled boolean.
*   `IO.java` - Added optional flushingEnabled constructor arg. Disabled
    flushing when flushingEnabled is false.

### Diffie-Hellman Group 14 SHA1 support

Added support for diffie-hellman-group14-sha1 key exchange algorithm to maintain
connections to servers using upgraded OpenSSL.

**Files edited:**

*   `JSch.java` - Added diffie-hellman-group14-sha1 to kex config.
*   `DHG14.java` - Added, patched from version 0.1.46.
*   `jce/DH.java` - Modified to support DH groups >1024 bits by ignoring JDK<8
    exception and testing length of keys.
