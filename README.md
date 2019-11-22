# POITracker
*An app that monitors POIs and notifies the user when approaching them*

As part of a two-day coding test I was required to create an app which is fed a list of POIs and then notifies the user when they enter or exit them. Due to Android limitations (not being able to set more than 100 geofences) the app only sets POIs when the user is in a city, otherwise it sets city geofences instead. 

The main focus of the assignment was obtaining reasonable precision while managing battery use and having as small an impact as possible. As such, the Geofencing API was the logical choice, but the problem remains that Doze interferes with it. The only way around that is to use a foreground service, but that makes the battery usage too high for such an app.

The app of course lacks some polish due to time constraints as well as my lack of experience using most of the concepts that were necessary to create this app. The app also uses RxJava as that was another requirement of the test. 
