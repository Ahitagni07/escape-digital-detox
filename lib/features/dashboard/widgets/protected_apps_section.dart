import 'package:flutter/material.dart';
import '../../../core/models/installed_app.dart';
import '../../escape/escape_controller.dart';

class ProtectedAppsSection extends StatelessWidget {
  final EscapeController controller;

  const ProtectedAppsSection({
    super.key,
    required this.controller,
  });

  Widget _appTile(InstalledApp app) {
    return SwitchListTile(
      value: controller.selectedPackages.contains(app.packageName),
      title: Text(app.name),
      subtitle: Text(
        app.browser
            ? 'Whole-browser protection'
            : 'Installed on this phone',
      ),
      secondary: Icon(
        app.browser ? Icons.language : Icons.phone_android,
      ),
      onChanged: controller.serviceRunning
          ? null
          : (enabled) {
              controller.togglePackage(
                app.packageName,
                enabled,
              );
            },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Protected apps',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        if (controller.socialApps.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'No supported social apps were detected on this phone.',
              ),
            ),
          )
        else
          ...controller.socialApps.map(_appTile),

        if (controller.browserApps.isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(
            'Browsers',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Text(
                'Android Usage Access can tell ESCAPE that a browser is open, '
                'but not which website is open. Selecting a browser here '
                'protects the WHOLE browser.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ),
          ...controller.browserApps.map(_appTile),
        ],
      ],
    );
  }
}
