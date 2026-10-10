import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/models/installed_app.dart';
import '../../core/native/escape_native_bridge.dart';

class EscapeController extends ChangeNotifier with WidgetsBindingObserver {
  final EscapeNativeBridge _native = EscapeNativeBridge();

  bool usagePermission = false;
  bool overlayPermission = false;
  bool activityPermission = false;

  bool serviceRunning = false;
  bool locked = false;
  bool missionActive = false;
  bool accessActive = false;
  String lockMode = 'walk';
  bool indoorChoiceAvailable = false;
  bool demoMode = false;
  bool showSetup = true;
  bool loading = true;

  int walkMinutes = 10;
  int minSteps = 600;
  int accessMinutes = 30;

  int accessRemainingSeconds = 0;
  int walkSeconds = 0;
  int walkSteps = 0;
  int walkTargetSeconds = 600;
  int minRequiredSteps = 600;
  int rideMeters = 0;
  int rideTargetMeters = 1500;
  String missionActivity = '';
  bool proofReady = false;
  bool stepSensorAvailable = true;
  String foregroundPackage = '';

  int interruptions = 0;
  int missionsCompleted = 0;
  int walksCompleted = 0;
  int reclaimedMinutes = 0;
  int stepsEarned = 0;
  int streakDays = 0;
  int emergencyUnlocksToday = 0;

  String missionTitle = 'Earn Your Scroll';
  String missionInstruction = 'Complete a screen-free mission first.';
  String missionSource = 'fallback';
  String missionProofTag = 'nature';
  String missionProofCode = '';
  bool missionGenerating = false;
  bool aiModelInstalled = false;
  bool aiEngineReady = false;

  String aiDownloadState = 'unknown';
  int aiDownloadProgress = 0;
  int aiDownloadedBytes = 0;
  int aiDownloadTotalBytes = 584417280;
  int aiDownloadReason = 0;
  String aiDownloadMessage = '';
  String aiLastError = '';
  bool aiAutoDownloadConfigured = false;
  bool aiWifiOnly = true;

  List<InstalledApp> installedApps = <InstalledApp>[];
  final Set<String> selectedPackages = <String>{};

  Timer? _poller;

  bool get allPermissions =>
      usagePermission && overlayPermission && activityPermission;

  List<InstalledApp> get socialApps =>
      installedApps.where((app) => !app.browser).toList();

  List<InstalledApp> get browserApps =>
      installedApps.where((app) => app.browser).toList();

  int get effectiveMissionSeconds => demoMode ? 60 : walkMinutes * 60;
  int get effectiveMinSteps => demoMode ? 30 : minSteps;
  int get effectiveAccessSeconds => demoMode ? 120 : accessMinutes * 60;

  Future<void> initialize() async {
    WidgetsBinding.instance.addObserver(this);

    await refreshPermissions();
    await loadInstalledApps();
    await loadSettings();
    try {
      await _native.ensureGemmaModel();
    } catch (_) {}
    await refreshStatus();

    loading = false;
    showSetup = !allPermissions;
    notifyListeners();

    _poller = Timer.periodic(
      const Duration(seconds: 1),
      (_) => refreshStatus(),
    );
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      refreshPermissions();
      refreshStatus();
    }
  }

  Future<void> refreshPermissions() async {
    try {
      final result = await _native.getPermissionStatus();
      usagePermission = result['usage'] == true;
      overlayPermission = result['overlay'] == true;
      activityPermission = result['activity'] == true;
      if (allPermissions) showSetup = false;
      notifyListeners();
    } catch (_) {}
  }

  Future<void> loadInstalledApps() async {
    try {
      final raw = await _native.getInstalledSupportedApps();
      installedApps = raw
          .map(
            (item) => InstalledApp.fromMap(
              Map<dynamic, dynamic>.from(item as Map),
            ),
          )
          .where((app) => app.packageName.isNotEmpty)
          .toList();
      notifyListeners();
    } catch (_) {}
  }

  Future<void> loadSettings() async {
    try {
      final result = await _native.getSettings();
      final savedWalkMinutes =
          (result['walkMinutes'] as num?)?.toInt() ?? 10;
      final savedMinSteps =
          (result['minSteps'] as num?)?.toInt() ?? 600;
      final savedAccessMinutes =
          (result['accessMinutes'] as num?)?.toInt() ?? 30;

      const allowedWalkMinutes = <int>{5, 10, 15, 20, 30};
      const allowedMinSteps = <int>{300, 600, 800, 1000, 1500};
      const allowedAccessMinutes = <int>{15, 30, 45, 60, 90};

      // Older/demo builds may have persisted values such as 1 minute,
      // 30 steps or 2 minutes of access. Normalize them before the UI
      // reaches DropdownButtonFormField, otherwise Flutter asserts.
      walkMinutes =
          allowedWalkMinutes.contains(savedWalkMinutes) ? savedWalkMinutes : 10;
      minSteps =
          allowedMinSteps.contains(savedMinSteps) ? savedMinSteps : 600;
      accessMinutes =
          allowedAccessMinutes.contains(savedAccessMinutes)
              ? savedAccessMinutes
              : 45;

      demoMode = result['demoMode'] == true;

      final packages = (result['packages'] as List<dynamic>? ?? <dynamic>[])
          .map((value) => value.toString())
          .toSet();

      selectedPackages
        ..clear()
        ..addAll(packages);

      if (selectedPackages.isEmpty) {
        selectedPackages.addAll(
          socialApps.take(2).map((app) => app.packageName),
        );
      }

      notifyListeners();
    } catch (_) {}
  }

  Future<void> refreshStatus() async {
    try {
      final result = await _native.getStatus();

      serviceRunning = result['running'] == true;
      locked = result['locked'] == true;
      missionActive = result['missionActive'] == true;
      accessActive = result['accessActive'] == true;
      accessRemainingSeconds =
          (result['accessRemainingSeconds'] as num?)?.toInt() ?? 0;
      lockMode = result['lockMode']?.toString() ?? 'walk';
      indoorChoiceAvailable = result['indoorChoiceAvailable'] == true;
      walkSeconds = (result['walkSeconds'] as num?)?.toInt() ?? 0;
      walkSteps = (result['walkSteps'] as num?)?.toInt() ?? 0;
      walkTargetSeconds = (result['walkTargetSeconds'] as num?)?.toInt() ?? 600;
      minRequiredSteps = (result['minRequiredSteps'] as num?)?.toInt() ?? 600;
      rideMeters = (result['rideMeters'] as num?)?.toInt() ?? 0;
      rideTargetMeters = (result['rideTargetMeters'] as num?)?.toInt() ?? 1500;
      missionActivity = result['missionActivity']?.toString() ?? '';
      proofReady = result['proofReady'] == true;
      stepSensorAvailable = result['stepSensorAvailable'] != false;
      foregroundPackage = result['foregroundPackage']?.toString() ?? '';

      interruptions = (result['interruptions'] as num?)?.toInt() ?? 0;
      missionsCompleted =
          (result['missionsCompleted'] as num?)?.toInt() ?? 0;
      walksCompleted = (result['walksCompleted'] as num?)?.toInt() ?? 0;
      reclaimedMinutes =
          (result['reclaimedMinutes'] as num?)?.toInt() ?? 0;
      stepsEarned = (result['stepsEarned'] as num?)?.toInt() ?? 0;
      streakDays = (result['streakDays'] as num?)?.toInt() ?? 0;
      emergencyUnlocksToday =
          (result['emergencyUnlocksToday'] as num?)?.toInt() ?? 0;

      missionTitle = result['missionTitle']?.toString() ?? 'Earn Your Scroll';
      missionInstruction = result['missionInstruction']?.toString() ??
          'Complete a screen-free mission first.';
      missionSource = result['missionSource']?.toString() ?? 'fallback';
      missionProofTag = result['missionProofTag']?.toString() ?? 'nature';
      missionProofCode = result['missionProofCode']?.toString() ?? '';
      missionGenerating = result['missionGenerating'] == true;
      aiModelInstalled = result['aiModelInstalled'] == true;
      aiEngineReady = result['aiEngineReady'] == true;

      aiDownloadState = result['downloadState']?.toString() ?? 'unknown';
      aiDownloadProgress =
          (result['downloadProgress'] as num?)?.toInt() ?? 0;
      aiDownloadedBytes =
          (result['downloadedBytes'] as num?)?.toInt() ?? 0;
      aiDownloadTotalBytes =
          (result['downloadTotalBytes'] as num?)?.toInt() ?? 584417280;
      aiDownloadReason =
          (result['downloadReason'] as num?)?.toInt() ?? 0;
      aiDownloadMessage = result['downloadMessage']?.toString() ?? '';
      aiLastError = result['aiLastError']?.toString() ?? '';
      aiAutoDownloadConfigured = result['autoDownloadConfigured'] == true;
      aiWifiOnly = result['wifiOnly'] != false;

      notifyListeners();
    } catch (_) {}
  }

  Future<void> openUsageSettings() => _native.openUsageSettings();
  Future<void> openOverlaySettings() => _native.openOverlaySettings();

  Future<void> requestActivityPermission() async {
    await _native.requestRuntimePermissions();
    await Future<void>.delayed(const Duration(milliseconds: 400));
    await refreshPermissions();
  }

  void openSetup() {
    showSetup = true;
    notifyListeners();
  }

  void closeSetupIfReady() {
    if (allPermissions) {
      showSetup = false;
      notifyListeners();
    }
  }

  void setDemoMode(bool value) {
    if (serviceRunning) return;
    demoMode = value;
    notifyListeners();
  }

  void setWalkMinutes(int value) {
    if (serviceRunning || demoMode) return;
    walkMinutes = value;
    notifyListeners();
  }

  void setMinSteps(int value) {
    if (serviceRunning || demoMode) return;
    minSteps = value;
    notifyListeners();
  }

  void setAccessMinutes(int value) {
    if (serviceRunning || demoMode) return;
    accessMinutes = value;
    notifyListeners();
  }

  void togglePackage(String packageName, bool enabled) {
    if (serviceRunning) return;
    if (enabled) {
      selectedPackages.add(packageName);
    } else {
      selectedPackages.remove(packageName);
    }
    notifyListeners();
  }

  Future<String?> startEscape() async {
    if (selectedPackages.isEmpty) {
      return 'Select at least one app to protect.';
    }

    if (!allPermissions) {
      showSetup = true;
      notifyListeners();
      return 'Finish setup first.';
    }

    await _native.saveSettings({
      'packages': selectedPackages.toList(),
      'walkMinutes': walkMinutes,
      'minSteps': minSteps,
      'accessMinutes': accessMinutes,
      'demoMode': demoMode,
      'walkSecondsTarget': effectiveMissionSeconds,
      'effectiveMinSteps': effectiveMinSteps,
      'accessSecondsTarget': effectiveAccessSeconds,
    });

    final ok = await _native.startMonitor();
    await refreshStatus();

    return ok
        ? 'ESCAPE is active. Complete a mission to earn social access.'
        : 'Could not start ESCAPE. Check permissions.';
  }

  Future<String> startMission() async {
    if (!serviceRunning || !locked) {
      return 'No mission is waiting right now.';
    }
    if (missionActive) {
      return 'Mission is already in progress.';
    }

    await _native.startMission(activity: 'walk');
    await Future<void>.delayed(const Duration(milliseconds: 250));
    await refreshStatus();
    return missionActive && missionActivity == 'walk'
        ? 'Outdoor quest started. Your walking steps and time are preserved after 18:00.'
        : 'Quest could not start. Check ESCAPE permissions.';
  }

  Future<String> startIndoorQuest() async {
    if (!serviceRunning || !locked) return 'No quest is waiting.';
    if (missionActive) return 'Finish your current quest first.';
    if (!indoorChoiceAvailable) return 'Indoor quests become available at 18:00.';
    try {
      await _native.startMission(activity: 'indoor');
      await Future<void>.delayed(const Duration(milliseconds: 250));
      await refreshStatus();
      return missionActive && missionActivity == 'indoor'
          ? 'Indoor quest started. Complete the activity and photograph the result.'
          : 'Could not start the indoor quest.';
    } on PlatformException catch (e) {
      return e.message ?? 'Could not start the indoor quest.';
    }
  }

  Future<String> startCycleQuest() async {
    try {
      await _native.startCycleQuest();
      await Future<void>.delayed(const Duration(milliseconds: 350));
      await refreshStatus();
      return missionActivity == 'cycle' && missionActive
          ? 'Cycle quest started. GPS distance is measured locally while ESCAPE protection is active.'
          : 'Could not start bicycle GPS tracking. Enable precise location and try again.';
    } on PlatformException catch (e) {
      return e.message ?? 'Bicycle tracking unavailable. Choose a walking quest instead.';
    }
  }

  Future<String> speakMission() async {
    final ok = await _native.speakMission();
    return ok ? 'Mission spoken using your phone’s offline voice.'
        : 'No offline English TTS voice available. Install one from Android Text-to-speech settings.';
  }

  Future<Map<String, dynamic>> captureMissionPhoto() async {
    if (!locked || !proofReady) return {
      'captured': false,
      'message': 'Complete the mission goal before capturing the photo.',
    };
    try {
      return await _native.captureMissionPhoto();
    } on PlatformException catch (error) {
      return {'captured': false, 'message': error.message ?? 'Camera failed.'};
    }
  }

  Future<Map<String, dynamic>> analyzeMissionPhoto() async {
    try {
      final result = await _native.analyzeMissionPhoto();
      await Future<void>.delayed(const Duration(milliseconds: 350));
      await refreshStatus();
      return result;
    } on PlatformException catch (error) {
      return {'approved': false, 'message': error.message ?? 'Analysis failed.'};
    }
  }

  Future<void> discardMissionPhoto() async {
    await _native.discardMissionPhoto();
  }

  Future<String> stopEscape() async {
    if (missionActive) {
      return 'Finish or emergency-unlock the active mission before pausing ESCAPE.';
    }
    try {
      await _native.stopMonitor();
      await refreshStatus();
      return 'ESCAPE protection paused.';
    } on PlatformException catch (e) {
      return e.message ?? 'Could not pause ESCAPE.';
    }
  }

  Future<String> triggerTestLock() async {
    if (!serviceRunning) return 'Start ESCAPE first.';
    await _native.triggerTestLock();
    await Future<void>.delayed(const Duration(milliseconds: 250));
    await refreshStatus();
    return 'Demo lock reset. Complete the mission to earn access.';
  }


  Future<String> importGemmaFromDownloads() async {
    try {
      final result = await _native.importGemmaModel();
      if (result['cancelled'] == true) {
        return 'Model selection cancelled.';
      }

      await Future<void>.delayed(const Duration(milliseconds: 250));
      await refreshStatus();

      return aiModelInstalled
          ? 'Gemma imported from Downloads and is ready offline.'
          : 'The selected file was not imported.';
    } on PlatformException catch (e) {
      return e.message ?? 'Could not import the Gemma model.';
    }
  }

  Future<String> resumeGemmaDownload() async {
    try {
      await _native.retryGemmaModelDownload();
      await Future<void>.delayed(const Duration(milliseconds: 250));
      await refreshStatus();

      if (aiDownloadState == 'manual-import-required') {
        return 'Choose the existing .litertlm file from Downloads.';
      }
      if (aiDownloadState == 'paused') {
        return aiDownloadMessage.isNotEmpty
            ? aiDownloadMessage
            : 'Android will resume the existing Gemma download automatically.';
      }
      if (aiDownloadState == 'downloading' || aiDownloadState == 'pending') {
        return 'Gemma download is active.';
      }
      if (aiDownloadState == 'installed') {
        return 'Gemma is ready offline.';
      }
      return aiDownloadMessage.isNotEmpty
          ? aiDownloadMessage
          : 'Gemma download retry requested.';
    } on PlatformException catch (e) {
      return e.message ?? 'Could not resume the Gemma download.';
    }
  }

  Future<String> emergencyUnlock() async {
    await _native.emergencyUnlock();
    await Future<void>.delayed(const Duration(milliseconds: 250));
    await refreshStatus();
    return 'Emergency social access granted.';
  }

  String displayPackage(String packageName) {
    for (final app in installedApps) {
      if (app.packageName == packageName) return app.name;
    }
    return packageName;
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poller?.cancel();
    super.dispose();
  }
}
