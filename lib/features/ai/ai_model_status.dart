class AiModelStatus {
  final bool installed;
  final String modelFileName;
  final int sizeBytes;
  final int expectedSizeBytes;
  final String recommendedFile;
  final String runtime;
  final String modelFamily;
  final String backend;
  final String lastError;
  final bool engineReady;
  final bool enginePreparing;
  final bool escapeRunning;

  final String downloadState;
  final int downloadProgress;
  final int downloadedBytes;
  final int downloadTotalBytes;
  final bool autoDownloadConfigured;
  final bool wifiOnly;
  final int downloadReason;
  final String downloadMessage;

  const AiModelStatus({
    required this.installed,
    required this.modelFileName,
    required this.sizeBytes,
    required this.expectedSizeBytes,
    required this.recommendedFile,
    required this.runtime,
    required this.modelFamily,
    required this.backend,
    required this.lastError,
    required this.engineReady,
    required this.enginePreparing,
    required this.escapeRunning,
    required this.downloadState,
    required this.downloadProgress,
    required this.downloadedBytes,
    required this.downloadTotalBytes,
    required this.autoDownloadConfigured,
    required this.wifiOnly,
    required this.downloadReason,
    required this.downloadMessage,
  });

  factory AiModelStatus.fromMap(Map<String, dynamic> map) {
    return AiModelStatus(
      installed: map['installed'] == true,
      modelFileName: map['modelFileName']?.toString() ?? '',
      sizeBytes: (map['sizeBytes'] as num?)?.toInt() ?? 0,
      expectedSizeBytes:
          (map['expectedSizeBytes'] as num?)?.toInt() ?? 584417280,
      recommendedFile: map['recommendedFile']?.toString() ?? '',
      runtime: map['runtime']?.toString() ?? 'LiteRT-LM',
      modelFamily: map['modelFamily']?.toString() ?? 'Gemma',
      backend: map['backend']?.toString() ?? 'CPU',
      lastError: map['lastError']?.toString() ?? '',
      engineReady: map['engineReady'] == true,
      enginePreparing: map['enginePreparing'] == true,
      escapeRunning: map['escapeRunning'] == true,
      downloadState: map['downloadState']?.toString() ?? 'unknown',
      downloadProgress: (map['downloadProgress'] as num?)?.toInt() ?? 0,
      downloadedBytes: (map['downloadedBytes'] as num?)?.toInt() ?? 0,
      downloadTotalBytes:
          (map['downloadTotalBytes'] as num?)?.toInt() ?? 0,
      autoDownloadConfigured: map['autoDownloadConfigured'] == true,
      wifiOnly: map['wifiOnly'] != false,
      downloadReason: (map['downloadReason'] as num?)?.toInt() ?? 0,
      downloadMessage: map['downloadMessage']?.toString() ?? '',
    );
  }

  String get sizeText {
    if (sizeBytes <= 0) return 'Not installed';
    return '${(sizeBytes / 1000000).toStringAsFixed(1)} MB';
  }

  String get downloadText {
    if (installed) return 'Ready offline';
    switch (downloadState) {
      case 'pending':
        return wifiOnly ? 'Waiting / queued (Wi-Fi preferred)' : 'Queued';
      case 'downloading':
        return 'Downloading $downloadProgress%';
      case 'paused':
        return 'Download paused — ${downloadProgress}%';
      case 'failed':
        return 'Download failed';
      case 'manual-import-required':
        return 'Choose existing model from Downloads';
      case 'not-configured':
        return 'Choose existing model from Downloads';
      default:
        return 'Not downloaded yet';
    }
  }
}
