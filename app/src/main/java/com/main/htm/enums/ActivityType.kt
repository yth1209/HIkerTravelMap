package com.main.htm.enums

enum class ActivityType(
    val classNo: Int,
    val speechText: String
) {
    NONE(-1, "No"),
    WALKING(1, "Walk"),
    RUNNING(2, "Run"),
    STANDING(3, "Stand"),
    SITTING(4, "Sit"),
    UPSTAIRS(5, "Up Stairs"),
    DOWNSTAIRS(6, "Down Stairs"),
    UPLOWSLOPE(7, "Up Slope"),
    DONWLOWSLOPE(8, "Down Slop"),
    UPELEVATOR(9, "Up Elevator"),
    DOWNELEVATOR(10, "Down Elevator"),
    OTHER(0, "Other");

    companion object{
        fun of(classNo: Int?): ActivityType? {
            return ActivityType.entries.find { it.classNo == classNo }
        }

        fun of(classNo: Double?): ActivityType {
            return ActivityType.entries.find { it.classNo == classNo?.toInt() } ?: NONE
        }

        fun usableEntries(): List<ActivityType> = ActivityType.entries.filter { it != NONE }.sortedBy { it.classNo }
    }
}