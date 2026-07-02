cmake_minimum_required(VERSION 3.16)
project(CareerTunerDesktop VERSION 0.2 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

# Qt 6.5+ : Quick(QML) · Network(HTTP/SSE) · Widgets(트레이) · Multimedia(음성 녹음)
find_package(Qt6 6.5 REQUIRED COMPONENTS Quick QuickControls2 Network Widgets Multimedia)
qt_standard_project_setup(REQUIRES 6.5)

qt_add_executable(CareerTunerDesktop
    main.cpp
    core/ApiClient.h          core/ApiClient.cpp
    core/SseClient.h          core/SseClient.cpp
    core/AuthService.h        core/AuthService.cpp
    core/SettingsStore.h      core/SettingsStore.cpp
    core/JobModel.h           core/JobModel.cpp
    core/InterviewSession.h   core/InterviewSession.cpp
    core/VoiceRecorder.h      core/VoiceRecorder.cpp
    core/NotificationPoller.h core/NotificationPoller.cpp
    core/AutoPrepRunner.h     core/AutoPrepRunner.cpp
    core/CollaborationClient.h core/CollaborationClient.cpp
    core/CommunityClient.h    core/CommunityClient.cpp
    core/CameraRecorder.h     core/CameraRecorder.cpp
)

# Theme.qml 은 싱글톤 — QML_SINGLETON_TYPE 지정 필요
set_source_files_properties(qml/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)

# 화면(QML)을 CareerTuner 모듈로 묶음 → main.cpp 의 loadFromModule("CareerTuner","Main")
qt_add_qml_module(CareerTunerDesktop
    URI CareerTuner
    VERSION 1.0
    QML_FILES
        qml/Theme.qml
        qml/Main.qml
        qml/LoginPage.qml
        qml/SessionThread.qml
        qml/HomeView.qml
        qml/ReportView.qml
        qml/DevicesPage.qml
        qml/SettingsPage.qml
        qml/CollaborationPage.qml
        qml/BoardPage.qml
        qml/NotificationCenter.qml
        qml/PhonePanel.qml
        qml/InputBar.qml
        qml/NewJobDialog.qml
)

target_link_libraries(CareerTunerDesktop PRIVATE
    Qt6::Quick
    Qt6::QuickControls2
    Qt6::Network
    Qt6::Widgets
    Qt6::Multimedia
)
