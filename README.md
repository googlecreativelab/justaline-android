# Just a Line - Android 
Just a Line is an [AR Experiment](https://experiments.withgoogle.com/ar) that lets you draw simple white lines in 3D space, on your own or together with a friend, and share your creation with a video. Draw by pressing your finger on the screen and moving the phone around the space. 

This app was written in Java using ARCore. [ARCore Cloud Anchors](https://developers.google.com/ar/develop/java/cloud-anchors/cloud-anchors-quickstart-android) enable Just a Line to pair two phones, allowing users to draw simultaneously in a shared space. Pairing works across Android and iOS devices, and drawings are synchronized live on Firebase Realtime Database.

This is not an official Google product, but an [AR Experiment](https://experiments.withgoogle.com/ar) that was developed at the Google Creative Lab in collaboration with [Uncorked Studios](https://www.uncorkedstudios.com/). 

Just a Line is also developed for iOS. The open source code for iOS can be found [here](https://github.com/googlecreativelab/justaline-ios).

[<img alt="Get it on Google Play" height="50px" src="https://play.google.com/intl/en_us/badges/images/apps/en-play-badge-border.png" />](https://play.google.com/store/apps/details?id=com.arexperiments.justaline&utm_source=github)

## Get started
To build the project, download and open it in Android Studio 3.0. All dependencies should automatically be fetched by Android Studio.

You will need to set up a cloud project with Firebase, ARCore, and with nearby messages enabled. Follow the setup steps in the [ARCore Cloud Anchors Quickstart guide](https://developers.google.com/ar/develop/java/cloud-anchors/cloud-anchors-quickstart-android). 

When you launch the app, it will prompt the Play Store to install ARCore if you don’t already have it. You can check if your device is supported on [this list](https://developers.google.com/ar/discover/#supported_devices).
