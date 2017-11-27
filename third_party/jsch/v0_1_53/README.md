# JSch 0.1.53

JSCH is a library for making SSH and SFTP connections from Java.  It is
released under a BSD-style license.  See its [project
page](http://www.jcraft.com/jsch) for further details.

## Local Modifications for Google

Define global ThreadFactory instance. This allows the library to be used on
App Engine, which doesn't allow apps to call `new Thread()`. To do this, you
must override `JSch.threadFactory` with something from GAE's ThreadManager.
Another global is also provided to disable calls to `Thread#setName` which
always crash on GAE, regardless of ThreadFactory.

### Files edited:

*   JSch.java - Defined new `threadFactory` and `useThreadNames` fields.
*   Util.java - Updated thread creation code.
*   ChannelDirectTCPIP.java - Updated thread creation code.
*   ChannelSubsystem.java - Updated thread creation code.
*   ChannelShell.java - Updated thread creation code.
*   Session.java - Updated thread creation code.
*   ChannelForwardedTCPIP.java - Updated thread creation code.
*   ChannelExec.java - Updated thread creation code.
