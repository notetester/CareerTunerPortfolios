cmake_minimum_required(VERSION 3.16)
project(CareerTunerDesktop VERSION 0.2 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

# Qt 6.8+ : Quick(QML) · Network(HTTP/SSE) · Widgets(트레이) · Multimedia(음성 녹음)
find_package(Qt6 6.8 REQUIRED COMPONENTS Quick QuickControls2 Network Widgets Multimedia)
qt_standard_project_setup(REQUIRES 6.8)

if (COMMAND qt_policy)
    qt_policy(SET QTP0004 NEW)
endif()

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
    core/PlannerClient.h      core/PlannerClient.cpp
    core/PlannerOverlayController.h core/PlannerOverlayController.cpp
    core/AutoPrepRunner.h     core/AutoPrepRunner.cpp
    core/AdClient.h           core/AdClient.cpp
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
        qml/Icon.qml
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
        qml/PlannerOverlay.qml
        qml/PhonePanel.qml
        qml/InputBar.qml
        qml/NewJobDialog.qml
)

if (WIN32)
    target_link_libraries(CareerTunerDesktop PRIVATE user32)
endif()

target_link_libraries(CareerTunerDesktop PRIVATE
    Qt6::Quick
    Qt6::QuickControls2
    Qt6::Network
    Qt6::Widgets
    Qt6::Multimedia
)

include(CTest)
if (BUILD_TESTING)
    find_package(Qt6 6.8 REQUIRED COMPONENTS Test)
    qt_add_executable(CareerTunerDesktopCoreTests
        tests/DesktopCoreTests.cpp
        core/ApiClient.h core/ApiClient.cpp
        core/CameraRecorder.h core/CameraRecorder.cpp
        core/CollaborationClient.h core/CollaborationClient.cpp
        core/InterviewSession.h core/InterviewSession.cpp
        core/JobModel.h core/JobModel.cpp
        core/SettingsStore.h core/SettingsStore.cpp
        core/VoiceRecorder.h core/VoiceRecorder.cpp
        core/PlannerOverlayController.h core/PlannerOverlayController.cpp
    )
    target_include_directories(CareerTunerDesktopCoreTests PRIVATE core)
    target_link_libraries(CareerTunerDesktopCoreTests PRIVATE
        Qt6::Test
        Qt6::Network
        Qt6::Widgets
        Qt6::Multimedia
    )
    if (WIN32)
        target_link_libraries(CareerTunerDesktopCoreTests PRIVATE user32)
    endif()
    add_test(NAME CareerTunerDesktopCoreTests COMMAND CareerTunerDesktopCoreTests)
    if (WIN32)
        # 일반 PowerShell에서도 Qt DLL 탐색 오류 대화상자 없이 ctest를 바로 실행할 수 있게 한다.
        set_tests_properties(CareerTunerDesktopCoreTests PROPERTIES
            ENVIRONMENT_MODIFICATION "PATH=path_list_prepend:$<TARGET_FILE_DIR:Qt6::Core>"
        )
    endif()
endif()
