import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class StatusHero extends StatelessWidget {
  final EscapeController controller;

  const StatusHero({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    final protectedNames = controller.installedApps
        .where((app) => controller.selectedPackages.contains(app.packageName))
        .map((app) => app.name)
        .take(4)
        .join(' • ');

    String status;
    if (!controller.serviceRunning) {
      status = 'FOCUS PROTECTION PAUSED';
    } else if (controller.accessActive) {
      status = 'SOCIAL ACCESS EARNED';
    } else if (controller.missionActive) {
      status = 'MISSION IN PROGRESS';
    } else {
      status = 'MISSION READY';
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              radius: 26,
              child: Icon(
                controller.accessActive
                    ? Icons.lock_open
                    : controller.serviceRunning
                        ? Icons.shield
                        : Icons.shield_outlined,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(status, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 6),
                  Text(
                    protectedNames.isEmpty
                        ? 'Choose apps below.'
                        : 'Protected: $protectedNames',
                  ),
                  const SizedBox(height: 4),
                  Text(
                    controller.demoMode
                        ? 'Demo: photo challenge or 1 min indoor activity → 2 min access'
                        : 'Hourly new challenges • ${controller.accessMinutes} min access per completed mission. After 18:00: indoor-only.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
