#Privacy within Save My Pics

My goal is to keep your data private, and the code public.

Practical constraints affect this goal, so the rest is a detailed
description of what you can expect.

First though, if you are technically inclined you can browse [the
source code](https://github.com/kbsriram/savemypics) to see what it
really does.

##Permissions requested

Save My Pics uses the standard [Android AccountManager](https://developer.android.com/reference/android/accounts/AccountManager.html) framework to register and store credentials for your accounts.

These permissions are used to access the AccountManager framework.

```
android.permission.GET_ACCOUNTS
android.permission.USE_CREDENTIALS
android.permission.MANAGE_ACCOUNTS
android.permission.AUTHENTICATE_ACCOUNTS
```

It also uses the standard [Android SyncAdapter](https://developer.android.com/reference/android/content/AbstractThreadedSyncAdapter.html) framework, which wakes it up efficiently after new pictures show up on your device.

These permissions are used to work within the SyncAdapter framework.

```
android.permission.READ_SYNC_SETTINGS
android.permission.WRITE_SYNC_SETTINGS
```

Save My Pics connects to the internet to upload your pictures. To
reduce your data usage, it also wants to know when you're on a wifi
network.

These permissions are used for this purpose.

```
android.permission.INTERNET
android.permission.ACCESS_WIFI_STATE
android.permission.ACCESS_NETWORK_STATE
```

Finally, it uses these permissions to read pictures from, and write
any downloaded pictures to your sdcard.

```
android.permission.READ_EXTERNAL_STORAGE
android.permission.WRITE_EXTERNAL_STORAGE
```

##Ads, tracking and analytics.

None within the app.

##Privacy of photos

Your photos are uploaded to albums grouped and named by month. They
are not shared with anyone. As long as Snapfish itself and your
account is secure, only you (and Snapfish) can view your photos.

Snapfish does **not** offer uploading via SSL. So as your pictures are
uploaded, they are potentially visible to anyone in control of the
networks that lie between you and Snapfish. (This is also true if you
use the regular website to upload pictures.)

Photos are not downloaded by default, but if you do enable it - they
are stored on your sdcard, and visible to **all** apps on your
device. (This is similar to photos you take from the Camera app on
your device.)

##Security of your credentials

Save My Pics stores access tokens for your accounts -- never your
password -- using the system-provided AccountManager database. This
database is protected under the `system` user using Android's
file-system based protections. This is the standard security used by
Android for any AccountManager-based account on your phone (like your
Google account.)

However, this database is stored unencrypted on most Android
devices. This can be a problem if you have a rooted phone, or a phone
that can be rooted without wiping the existing data. In this case,
malware or someone who gains physical access to your phone may be able
to recover these tokens and access your account. This is true of all
accounts (like your Google account) linked on your phone.
