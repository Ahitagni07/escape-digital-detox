import 'package:flutter/material.dart';
import '../escape/escape_controller.dart';

class SetupScreen extends StatelessWidget {
  final EscapeController controller;

  const SetupScreen({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    if (controller.allPermissions) {
      return Scaffold(
        appBar: AppBar(title: const Text('ESCAPE 🌱')),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              const SizedBox(height: 24),
              const Icon(Icons.check_circle, size: 72),
              const SizedBox(height: 16),
              Text(
                'ESCAPE is ready',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 20),
              const ListTile(
                leading: Icon(Icons.check),
                title: Text('Usage monitoring'),
                subtitle: Text('ESCAPE can detect protected apps.'),
              ),
              const ListTile(
                leading: Icon(Icons.check),
                title: Text('App blocking'),
                subtitle: Text('ESCAPE can show the recovery lock screen.'),
              ),
              const ListTile(
                leading: Icon(Icons.check),
                title: Text('Step tracking'),
                subtitle: Text('ESCAPE can verify your recovery walk.'),
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: controller.closeSetupIfReady,
                child: const Padding(
                  padding: EdgeInsets.symmetric(vertical: 14),
                  child: Text('CONTINUE TO ESCAPE'),
                ),
              ),
            ],
          ),
        ),
      );
    }

    int step;
    String title;
    String explanation;
    String instruction;
    IconData icon;
    Future<void> Function() action;
    String button;

    if (!controller.usagePermission) {
      step = 1;
      title = 'Allow usage monitoring';
      explanation =
          'ESCAPE needs to know when a protected app such as Instagram or '
          'YouTube is in the foreground. It does not read messages, posts, '
          'passwords, or screen contents.';
      instruction =
          'Android will open Usage Access. Find “ESCAPE”, tap it, then enable '
          '“Permit usage access”. After that, return here.';
      icon = Icons.insights_outlined;
      action = controller.openUsageSettings;
      button = 'OPEN USAGE ACCESS';
    } else if (!controller.overlayPermission) {
      step = 2;
      title = 'Allow app blocking';
      explanation =
          'ESCAPE needs permission to show its lock screen above a protected '
          'social app after you reach your limit.';
      instruction =
          'Android will open “Display over other apps”. Find “ESCAPE”, tap it, '
          'then enable “Allow display over other apps”. Return here afterwards.';
      icon = Icons.lock_outline;
      action = controller.openOverlaySettings;
      button = 'OPEN DISPLAY SETTINGS';
    } else {
      step = 3;
      title = 'Allow step tracking';
      explanation =
          'ESCAPE uses the phone’s step counter to verify that your recovery '
          'mission is actually a walk.';
      instruction =
          'Tap Allow and approve the Physical Activity permission when Android '
          'asks. Step counts stay on your phone.';
      icon = Icons.directions_walk;
      action = controller.requestActivityPermission;
      button = 'ALLOW PHYSICAL ACTIVITY';
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('ESCAPE 🌱'),
        actions: [
          if (controller.allPermissions)
            TextButton(
              onPressed: controller.closeSetupIfReady,
              child: const Text('DONE'),
            ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const SizedBox(height: 12),
            Text(
              'A tiny setup before ESCAPE can protect your attention.',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 24),
            LinearProgressIndicator(value: step / 3),
            const SizedBox(height: 8),
            Text('Step $step of 3'),
            const SizedBox(height: 28),
            Icon(icon, size: 64),
            const SizedBox(height: 20),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 12),
            Text(
              explanation,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyLarge,
            ),
            const SizedBox(height: 24),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  '👉 $instruction',
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
              ),
            ),
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: () => action(),
              icon: const Icon(Icons.settings),
              label: Padding(
                padding: const EdgeInsets.symmetric(vertical: 14),
                child: Text(button),
              ),
            ),
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: controller.refreshPermissions,
              child: const Text('I DID IT — CHECK AGAIN'),
            ),
            const SizedBox(height: 28),
            const Text(
              'Privacy note: ESCAPE only uses app package names, timers and '
              'step counts. It does not inspect social-media content.',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
