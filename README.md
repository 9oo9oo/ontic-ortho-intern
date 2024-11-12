## Overview

This application is an Android application designed to scan the physical model of the bone and compare them to its identical .obj file. 
Utilising OpenGL and OpenCV, the application detects feature points from the physical model, compare them to 3D points of .obj file, and calculates the match percentage. 

---

## Prerequisites

To run the application, ensure you have the following:
- Latest version of Android Studio
- Physical android device with USB debugging enabled
  - Follow the instructions from here: https://developer.android.com/studio/debug/dev-options#Enable-debugging

---

## Setup Instructions

1. Download and Install Android Studio
- Download from here: https://developer.android.com/studio
2. Clone the repository
- Open terminal and navigate to the directory you want to clone the project
- Run the command:
- git clone https://bitbucket.org/9oo9oo/ontic-ortho-placement.git
3. Open the project in Android Studio
- Launch Android Studio
- Click on "Open..."
- Navigate to the cloned repository folder
- Click "OK" to open the project
4. Connect your Android Device to computer / laptop via USB
5. Run the application (green triangle button on toolbar)

---

## CAD file information

I've made some modifications to the Original .stl file as the application initially had issues recognising the model. All the modifications were made by using Blender.
1. Conversion from '.stl' to '.obj'
2. Origin point of the model set to (0, 0, 0)
3. Minor simplifications / normalisation

The '.obj' file can be located in the 'app/src/main/assets/' directory of the project.

If you are replacing the CAD file, ensure your CAD file is in '.obj' format, and the model is placed in the centre of axis.
Make sure to change the name of the file in the MainActivity class as well.

---

## Dependencies

Only external dependency was OpenCV, which is already included in the project repository. 
There is no need to download and integrate OpenCV separately into the project. 

---

## Contact

For any questions or issues, please contact:
Name: Jin
Email: jinhyeokgwak@gmail.com

