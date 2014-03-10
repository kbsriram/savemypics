Code placed here is simply stuff that can be conveniently tested
without requiring the Android runtime.

A "plugin" is effectively a chunk of utilities that provide ways to
sign, upload, download, and get details about pictures for a given
site.

To actually run the tests here successfully, you'll need to plug in a
property file containing appkeys for the sites in question; and also
user tokens for test users that were obtained manually.

Create a file called test/app.properties; with suitable values for:

```
flickr.appid
flickr.appsecret
```

These should come from a previously authenticated call.

```
flickr.usertoken
flickr.usersecret
flickr.usernsid
```

And it is similar for Snapfish.

```
snapfish.appid
snapfish.appsecret
```

This should contain the actual json object from a snapfish oauth refresh
request response.


```
snapfish.refresh= {"upload_end_point":"...","expires_in":"...","refresh_token":"...","access_token":"...","rest_end_point":"...", ...}
```

and appropriate values for the associated account

```
snapfish.testemail
snapfish.testpass
snapfish.testalbumtitle
```
