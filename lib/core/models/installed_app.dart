class InstalledApp {
  final String name;
  final String packageName;
  final bool browser;

  const InstalledApp({
    required this.name,
    required this.packageName,
    required this.browser,
  });

  factory InstalledApp.fromMap(Map<dynamic, dynamic> map) {
    return InstalledApp(
      name: map['name']?.toString() ?? 'Unknown',
      packageName: map['packageName']?.toString() ?? '',
      browser: map['browser'] == true,
    );
  }
}
