cmake_minimum_required(VERSION 3.16)
project(CareerTunerDesktop VERSION 0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

# Qt 6.5+ : Quick(QML) · Network(HTTP/SSE) · Widgets(트레이 아이콘)
find_package(Qt6 6.5 REQUIRED COMPONENTS Quick QuickControls2 Network Widgets)
qt_standard_project_setup(REQUIRES 6.5)

qt_add_executable(CareerTunerDesktop
    main.cpp
    core/ApiClient.h    core/ApiClient.cpp
    core/SseClient.h    core/SseClient.cpp
    core/AuthService.h  core/AuthService.cpp
    core/JobModel.h     core/JobModel.cpp
)

# 화면(QML)을 CareerTuner 모듈로 묶음 → main.cpp 의 loadFromModule("CareerTuner","Main")
qt_add_qml_module(CareerTunerDesktop
    URI CareerTuner
    VERSION 1.0
    QML_FILES
        qml/Main.qml
        qml/DashboardPage.qml
        qml/JobDetailPage.qml
        qml/PracticePage.qml
        qml/ReportPage.qml
        qml/PhoneMirror.qml
)

target_link_libraries(CareerTunerDesktop PRIVATE
    Qt6::Quick
    Qt6::QuickControls2
    Qt6::Network
    Qt6::Widgets
)
