#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <algorithm>
#include <android/log.h>

#include <opencv2/opencv.hpp>
#include <opencv2/dnn.hpp>

#define TAG "PersonClassifierJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace cv;
using namespace cv::dnn;
using namespace std;

// Global variables for the neural networks
Net faceNet;
Net ageNet;
Net genderNet;

vector<string> ageList = {"(0-2)", "(4-6)", "(8-12)", "(15-20)",
                          "(25-32)", "(38-43)", "(48-53)", "(60-100)"};
vector<string> genderList = {"Male", "Female"};
Scalar MODEL_MEAN_VALUES = Scalar(78.4263377603, 87.7689143744, 114.895847746);

void verifyModelPaths(const string& prototxt, const string& model) {
    ifstream proto(prototxt);
    ifstream mod(model);

    if (!proto.good() || !mod.good()) {
        LOGE("Error: Model files not found or inaccessible! Check paths for: %s and %s", prototxt.c_str(), model.c_str());
        // In a real app, you might throw an exception or return an error code
    }
}

void highlightFace(Net& net, Mat& frame, vector<Rect>& faceBoxes, float confThreshold = 0.7) {
    Mat frameCopy = frame.clone();

    // UPDATED: No cvtColor needed; assume frameCopy is already BGR
    // Use swapRB=false for Caffe SSD (expects BGR)
    Mat blob = blobFromImage(frameCopy, 1.0, Size(300, 300), Scalar(104, 117, 123), false, false);

    net.setInput(blob);
    Mat detections = net.forward();
    Mat detectionMat(detections.size[2], detections.size[3], CV_32F, detections.ptr<float>());

    faceBoxes.clear();
    for (int i = 0; i < detectionMat.rows; i++) {
        float confidence = detectionMat.at<float>(i, 2);

        if (confidence > confThreshold) {
            int x1 = static_cast<int>(detectionMat.at<float>(i, 3) * frame.cols);
            int y1 = static_cast<int>(detectionMat.at<float>(i, 4) * frame.rows);
            int x2 = static_cast<int>(detectionMat.at<float>(i, 5) * frame.cols);
            int y2 = static_cast<int>(detectionMat.at<float>(i, 6) * frame.rows);

            x1 = max(0, x1);
            y1 = max(0, y1);
            x2 = min(frame.cols-1, x2);
            y2 = min(frame.rows-1, y2);

            faceBoxes.push_back(Rect(x1, y1, x2-x1, y2-y1));
        }
    }
}

string getPersonLabel(const string& gender, const string& ageRange) {
    size_t dashPos = ageRange.find("-");
    int minAge = stoi(ageRange.substr(1, dashPos-1));

    if (gender == "Male") {
        return (minAge < 18) ? "Boy" : "Man";
    } else {
        return (minAge < 18) ? "Girl" : "Woman";
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_personclassifierapp_YourNativeClass_initClassifier(
        JNIEnv* env, jobject /* this */, jstring faceProtoPath, jstring faceModelPath,
        jstring ageProtoPath, jstring ageModelPath, jstring genderProtoPath, jstring genderModelPath) {

    const char* faceProtoCStr = env->GetStringUTFChars(faceProtoPath, 0);
    const char* faceModelCStr = env->GetStringUTFChars(faceModelPath, 0);
    const char* ageProtoCStr = env->GetStringUTFChars(ageProtoPath, 0);
    const char* ageModelCStr = env->GetStringUTFChars(ageModelPath, 0);
    const char* genderProtoCStr = env->GetStringUTFChars(genderProtoPath, 0);
    const char* genderModelCStr = env->GetStringUTFChars(genderModelPath, 0);

    verifyModelPaths(faceProtoCStr, faceModelCStr);
    verifyModelPaths(ageProtoCStr, ageModelCStr);
    verifyModelPaths(genderProtoCStr, genderModelCStr);

    faceNet = readNetFromCaffe(faceProtoCStr, faceModelCStr);
    ageNet = readNetFromCaffe(ageProtoCStr, ageModelCStr);
    genderNet = readNetFromCaffe(genderProtoCStr, genderModelCStr);

    env->ReleaseStringUTFChars(faceProtoPath, faceProtoCStr);
    env->ReleaseStringUTFChars(faceModelPath, faceModelCStr);
    env->ReleaseStringUTFChars(ageProtoPath, ageProtoCStr);
    env->ReleaseStringUTFChars(ageModelPath, ageModelCStr);
    env->ReleaseStringUTFChars(genderProtoPath, genderProtoCStr);
    env->ReleaseStringUTFChars(genderModelPath, genderModelCStr);

    LOGD("Classifier initialized successfully.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_personclassifierapp_YourNativeClass_processFrame(
        JNIEnv* env, jobject /* this */, jlong matAddrRgba) {

    Mat& frame = *(Mat*)matAddrRgba;

    vector<Rect> faceBoxes;
    highlightFace(faceNet, frame, faceBoxes);

    string result = "";
    for (size_t i = 0; i < faceBoxes.size(); i++) {
        Rect faceBox = faceBoxes[i];

// Add padding around the face
        int padding = static_cast<int>(0.2 * max(faceBox.width, faceBox.height));
        Rect faceRect(
                max(0, faceBox.x - padding),
                max(0, faceBox.y - padding),
                min(faceBox.width + 2*padding, frame.cols - max(0, faceBox.x - padding)),
                min(faceBox.height + 2*padding, frame.rows - max(0, faceBox.y - padding))
        );

        Mat face = frame(faceRect);

// Gender detection
        Mat genderBlob = blobFromImage(face, 1.0, Size(227, 227), MODEL_MEAN_VALUES, false);
        genderNet.setInput(genderBlob);
        Mat genderPreds = genderNet.forward();
        string gender = genderList[genderPreds.at<float>(0, 0) > genderPreds.at<float>(0, 1) ? 0 : 1];

// Age detection
        Mat ageBlob = blobFromImage(face, 1.0, Size(227, 227), MODEL_MEAN_VALUES, false);
        ageNet.setInput(ageBlob);
        Mat agePreds = ageNet.forward();

        Point ageMaxLoc;
        minMaxLoc(agePreds.reshape(1, 1), nullptr, nullptr, nullptr, &ageMaxLoc);
        string ageRange = ageList[ageMaxLoc.x];

// Get person label
        string label = getPersonLabel(gender, ageRange);

        result += "Detected: " + label + " (Gender: " + gender + ", Age: " + ageRange + ")\n";
        result += "Face position - X: " + to_string(faceBox.x) + ", Y: " + to_string(faceBox.y) + ", Width: " + to_string(faceBox.width) + ", Height: " + to_string(faceBox.height) + "\n";

// Draw results on the frame (optional, can be done in Kotlin/Java)
        rectangle(frame, faceBox, Scalar(0, 255, 0), 2);
        string displayText = label + " " + ageRange;
        putText(frame, displayText, Point(faceBox.x, faceBox.y-10), FONT_HERSHEY_SIMPLEX, 0.8, Scalar(0, 255, 255), 2);
    }

    return env->NewStringUTF(result.c_str());
}
