

# ImageCarousel

This is my solution to the Shutterfly interview project  I would like to summarize things here.

# Using the app
- A debug apk is stored in the apk/ directory
- The app has a title bar area, canvas area and an image carousel populated with the images.
- In order to make the carousel easily scrollable the user must long press on a carousel image in order to place the image on the canvas.
- Once an image is on the canvas it has the same height and width as the original bitmap.  Zooming can be accomplished by pinching the image.
- Once an image is zoomed it can be panned within the image frame.
- If the user long presses on a canvas image it can be moved around the canvas.  It will also be brought to the front of the image layers as
the images can be overlayed on top of each other.
- Images cannot be removed from the canvas once they have been placed.
- Images do not disappear from the carousel when placed on the canvas.  Multiples of the same image can be put on the canvas.
- The app is locked in portrait mode.  Explanation below.


The solution utilizes the following:
* Repository pattern
* DI using Hilt
* MVVM
* Clean Architecture
* Flow
* Jetpack Compose
* Unit testing using JUnit and Mockito

I use the repository pattern to demonstrate support for scalability and modularity.  The project loads images from the assets directory
but also has a stub for loading from the device media storage, though this is not implemented.

The data is very simple but in a more robust project there would be more metadata associated with images so the DTO pattern is used to isolate
the data objects from the domain objects.  This is unnecessary for this data but I wanted to show how this would be implemented.

There is a single use case that loads the images from the repository.  This is done done to support testability and avoid unnecessary bloat in the view model.
It utilizes flow in order to support loading, error and image loaded states.  This is also not required as failure to load from the assets directory is not going to
happen but it demonstrates using flow to support the UI state.

The viewmodel class loads the images from the repository and maintains a list of those images.  It also maintains a list of the images that have been put on the canvas.
These images are kept in a new data class in order to support unique ID, offset, scale, etc.  It provides methods to modify the content and placement data of those objects.
It will be the source of truth for the carousel as well as the canvas.  This would be important if the app could be put into landscape mode but it cannot.  This is because
the canvas size changes upon device rotation and the images would need to be translated into this new size but that is beyond the scope of this project.

There is a UiState class that is used to keep separation of the domain state and the UI state.  This is also unnecessary for a project this simple but is considered
good architectural practice in a more complex scenario.

The image carousel is in its own composable but the image canvas is not.  The complexity of the state lifting in order to move the canvas was more work than
necessary for this project.  With more time the ideal situation would have been to move this.  It also would have reduced the size of the main screen which is
ideal.

Two unit tests have been added for the repository and the use case.  These tests provide 100% coverage for these classes.  No UI tests have been added.