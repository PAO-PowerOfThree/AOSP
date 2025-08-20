package com.example.personclassifierapp

// No imports needed for Log or System after removing the companion object

class YourNativeClass {

    // These external function declarations are correct.
    external fun initClassifier(
        faceProtoPath: String,
        faceModelPath: String,
        ageProtoPath: String,
        ageModelPath: String,
        genderProtoPath: String,
        genderModelPath: String
    )

    external fun processFrame(matAddrRgba: Long): String

    // The companion object that was trying to load the library has been completely removed.
}

