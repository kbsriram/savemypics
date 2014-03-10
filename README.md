# Save My Pics

This is the code for [Save My Pics](https://play.google.com/store/apps/details?id=org.savemypics.android), an Android app that runs silently in the background and uploads new pictures to photo providers.

[Snapfish](https://www.snapfish.com) and
[Flickr](https://www.flickr.com) are currently integrated, but Flickr
is disabled because I couldn't figure out how to remain within their
API quota limits for a general purpose app like this.

The only pieces missing from this source are the actual application
keys and secrets used to connect to the provider sites. In order to
build this application, you must register as a developer and obtain
suitable keys directly from the provider. Then, create a couple of
java files named `KeyFish.java` and `KeyFlickr.java` containing
constructor and getter methods for the application key and
secret. Please look at
(android/src/org/savemypics/android/glue/CSnapfishGlue.java) and
(android/src/org/savemypics/android/glue/CFlickrGlue.java) for details.

If you want to run the test cases, you must also place your
application keys and create tokens for a test user -- please read
(plugins/README.md) for details.

# Privacy

My aim is to protect the privacy of your pictures upto the maximum
extent possible with the online photo provider you choose. I've made
the source code available partly so anyone can look at exactly what it
really does, you don't have to rely on lofty claims from me. The
source is also available under [the Simplified BSD
License](LICENCE.md).

I've created a separate [privacy page](privacy.md) to list what you
should expect when using the app.

If you find bugs or have suggestions, please do file an issue -- I'd
love to hear from you.
