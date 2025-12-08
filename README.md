# Self Tracker - Android Application

A beautiful, feature-rich Android application for tracking daily habits and long-term goals with streak functionality and progress visualization.

## Features

### 1. Daily Habit Tracking
- Create daily habits with target values and units
- Track completion status for each day
- Flexible scheduling (Fixed Time or Any Time)
- Visual streak tracking (current and best)
- Automatic streak calculation

### 2. Long-Term Goal Management
- Set ambitious long-term goals
- Add detailed descriptions
- Track target completion dates
- View days remaining until goal deadline
- Mark goals as completed

### 3. Progress Reporting
- Monthly calendar view showing completion status
- Visual indicators for completed, missed, and today's date
- Progress percentage calculation
- Detailed statistics per habit
- Historical tracking data

### 4. Beautiful UI
- Material Design 3 inspired interface
- Purple and teal color scheme
- Smooth animations and transitions
- Responsive layout for all screen sizes
- Bottom navigation for easy access

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI Framework | Android Views (XML Layouts) |
| Database | Room (SQLite) |
| Architecture | MVVM Pattern |
| Build System | Gradle |
| Minimum SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |

## Project Structure

```
self_tracker_xml/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/selftracker/
│   │   │   ├── activities/          (Activity classes)
│   │   │   ├── database/            (Room DAOs)
│   │   │   ├── models/              (Data models)
│   │   │   └── utils/               (Utility functions)
│   │   └── res/
│   │       ├── layout/              (XML layout files)
│   │       ├── values/              (Strings, colors, styles)
│   │       ├── menu/                (Menu resources)
│   │       ├── xml/                 (Configuration files)
│   │       └── mipmap-*/            (App icons)
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Installation & Build

### Prerequisites

1. **Java Development Kit (JDK) 17 or higher**
   - Download from: https://www.oracle.com/java/technologies/downloads/

2. **Android SDK**
   - Install via Android Studio or command line
   - Minimum API 24, Target API 34

3. **Android Studio** (Recommended)
   - Download from: https://developer.android.com/studio

### Build Steps

#### Option 1: Using Android Studio (Recommended)

1. **Open the project**
   - Launch Android Studio
   - Click "Open" and select the `self_tracker_xml` folder
   - Wait for Gradle sync to complete

2. **Build the APK**
   - Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
   - Wait for the build to complete
   - APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

3. **Install on device**
   - Connect your Android device via USB
   - Enable USB debugging on your device
   - Click **Run → Run 'app'** in Android Studio
   - Select your device and click OK

#### Option 2: Using Command Line

```bash
# Navigate to project directory
cd self_tracker_xml

# Build APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.example.selftracker/.activities.MainActivity
```

### Build Time
- First build: 3-5 minutes
- Subsequent builds: 1-2 minutes

### APK Size
- Debug APK: ~15MB
- Release APK: ~12MB

## Usage Guide

### Creating a Habit

1. Open the **Habits** tab
2. Click the **+** button (Floating Action Button)
3. Enter habit details:
   - **Habit Name**: e.g., "Morning Exercise"
   - **Target Value**: e.g., "30"
   - **Unit**: e.g., "minutes"
4. Click **Save**

### Marking Habit as Complete

1. Go to **Habits** tab
2. Find your habit in the list
3. Click the **Complete** button
4. Streak will automatically increment

### Creating a Goal

1. Open the **Goals** tab
2. Click the **+** button (Floating Action Button)
3. Enter goal details:
   - **Goal Name**: e.g., "Get Microsoft Job"
   - **Description**: e.g., "Land a job at Microsoft"
   - **Target Date**: e.g., "2024-12-31"
4. Click **Save**

### Viewing Progress

1. Go to **Progress** tab
2. View statistics for each habit:
   - Current Streak
   - Best Streak
   - Completed count
3. See monthly calendar with completion indicators
4. View progress percentage

## Database Schema

### Goals Table
```sql
CREATE TABLE goals (
    goalId INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    targetDate TEXT NOT NULL,
    createdAt TEXT NOT NULL,
    isCompleted BOOLEAN DEFAULT 0
);
```

### Goal Steps Table
```sql
CREATE TABLE goal_steps (
    stepId INTEGER PRIMARY KEY AUTOINCREMENT,
    goalId INTEGER NOT NULL,
    name TEXT NOT NULL,
    isCompleted BOOLEAN DEFAULT 0,
    completionDate TEXT,
    FOREIGN KEY(goalId) REFERENCES goals(goalId)
);
```

### Habits Table
```sql
CREATE TABLE habits (
    habitId INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    targetValue INTEGER NOT NULL,
    unit TEXT NOT NULL,
    scheduleType TEXT NOT NULL,
    fixedTime TEXT,
    currentStreak INTEGER DEFAULT 0,
    bestStreak INTEGER DEFAULT 0,
    lastCompletedDate TEXT
);
```

### Habit Logs Table
```sql
CREATE TABLE habit_logs (
    logId INTEGER PRIMARY KEY AUTOINCREMENT,
    habitId INTEGER NOT NULL,
    date TEXT NOT NULL,
    isCompleted BOOLEAN DEFAULT 0,
    actualValue INTEGER DEFAULT 0,
    FOREIGN KEY(habitId) REFERENCES habits(habitId)
);
```

## Key Files

### Activities
- **MainActivity.kt**: Main entry point with bottom navigation
- **HabitsActivity.kt**: Daily habit tracking interface
- **GoalsActivity.kt**: Long-term goal management
- **ProgressActivity.kt**: Progress reporting and calendar view

### Database
- **SelfTrackerDatabase.kt**: Room database configuration
- **GoalDao.kt**: Goal data access operations
- **GoalStepDao.kt**: Goal step data access operations
- **HabitDao.kt**: Habit data access operations
- **HabitLogDao.kt**: Habit log data access operations

### Models
- **Goal.kt**: Goal entity
- **GoalStep.kt**: Goal step entity
- **Habit.kt**: Habit entity
- **HabitLog.kt**: Habit log entity

### Layouts
- **activity_main.xml**: Main activity layout with bottom navigation
- **activity_habits.xml**: Habits screen layout
- **activity_goals.xml**: Goals screen layout
- **activity_progress.xml**: Progress screen layout
- **item_habit_card.xml**: Habit card component
- **item_goal_card.xml**: Goal card component
- **item_progress_card.xml**: Progress card component
- **dialog_add_habit.xml**: Add habit dialog
- **dialog_add_goal.xml**: Add goal dialog

## Troubleshooting

### Build Errors

**Error: "Android resource linking failed"**
- Solution: Clean and rebuild the project
  ```bash
  ./gradlew clean
  ./gradlew assembleDebug
  ```

**Error: "Gradle sync failed"**
- Solution: Update Gradle and dependencies
  ```bash
  ./gradlew --version
  ```

**Error: "SDK not found"**
- Solution: Update `local.properties` with correct SDK path
  ```
  sdk.dir=/path/to/android/sdk
  ```

### Installation Issues

**Error: "Device not found"**
- Ensure USB debugging is enabled on your device
- Check USB connection
- Run: `adb devices`

**Error: "Installation failed"**
- Uninstall previous version: `adb uninstall com.example.selftracker`
- Try again

## Development

### Adding a New Feature

1. Create layout XML file in `res/layout/`
2. Create Activity class in `activities/`
3. Add to AndroidManifest.xml
4. Implement business logic in Activity
5. Update database if needed

### Modifying Colors

Edit `app/src/main/res/values/colors.xml`:
```xml
<color name="primary">#6200EE</color>
<color name="secondary">#03DAC5</color>
```

### Modifying Strings

Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">Self Tracker</string>
```

## Performance Tips

1. **Database Optimization**
   - Queries are indexed for fast access
   - Use LiveData for reactive updates
   - Coroutines handle long operations

2. **UI Optimization**
   - Layouts are optimized for performance
   - CardView provides efficient rendering
   - RecyclerView ready for large lists

3. **Memory Management**
   - Proper lifecycle management
   - Coroutines prevent memory leaks
   - Database connections are pooled

## Future Enhancements

- Push notifications for habit reminders
- Habit templates for quick creation
- Custom categories and tags
- Export progress reports as PDF
- Dark mode support
- Cloud backup and sync
- Social sharing features
- Advanced analytics and insights

## Support & Resources

- **Android Developer Docs**: https://developer.android.com
- **Kotlin Documentation**: https://kotlinlang.org/docs
- **Room Database**: https://developer.android.com/training/data-storage/room
- **Material Design**: https://material.io/design

## License

This project is free and open-source. Feel free to use, modify, and distribute as needed.

## Contributing

Contributions are welcome! Feel free to fork the project and submit pull requests.

## Contact

For questions or suggestions, please create an issue in the project repository.

---

**Happy Tracking! 🎯**

Start building your habits and achieving your goals today with Self Tracker!
