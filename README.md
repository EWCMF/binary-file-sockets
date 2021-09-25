# binary-file-sockets

Two simple self-contained JavaFX applications to send data via sockets in a local network. 
The port used are 1337 and 1338 so make sure those aren't already in use if you wish to use the applications.

The app images were built on a Windows PC and therefore only work on that OS. Versions compatible with other systems might be built at another time. 

The apps use MD5 checksum to ensure file integrity and also support resuming downloads after dropped connections.

Check your firewall if the sending app's 'scan network' function doesn't find a PC running the receiving app. It might be blocked.

The output location is the downloads folder of the current user.