import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class RulesCard extends StatelessWidget {
  final EscapeController controller;
  const RulesCard({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    const indoorMinutes = <int>[5, 10, 15, 20, 30];
    const accessOptions = <int>[15, 30, 45, 60, 90];
    final indoor = indoorMinutes.contains(controller.walkMinutes)
        ? controller.walkMinutes : 10;
    final access = accessOptions.contains(controller.accessMinutes)
        ? controller.accessMinutes : 45;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Your screen-time agreement',
                style: Theme.of(context).textTheme.titleMedium),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: controller.demoMode,
              title: const Text('Demo mode'),
              subtitle: const Text('1-minute indoor timer and 2-minute reward. Notifications still hourly.'),
              onChanged: controller.serviceRunning ? null : controller.setDemoMode,
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<int>(
              value: indoor,
              decoration: const InputDecoration(labelText: 'Evening activity duration'),
              items: indoorMinutes.map((x) => DropdownMenuItem(
                value: x, child: Text('$x minutes'))).toList(),
              onChanged: controller.serviceRunning || controller.demoMode
                  ? null : (v) { if (v != null) controller.setWalkMinutes(v); },
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              value: access,
              decoration: const InputDecoration(labelText: 'Social access earned per mission'),
              items: accessOptions.map((x) => DropdownMenuItem(
                value: x, child: Text('$x minutes'))).toList(),
              onChanged: controller.serviceRunning || controller.demoMode
                  ? null : (v) { if (v != null) controller.setAccessMinutes(v); },
            ),
            const SizedBox(height: 10),
            const Text('A new creative mission is suggested every hour while social access is locked. '
                'Day: upload a nature photo. Evening: screen-free indoor activity.'),
          ],
        ),
      ),
    );
  }
}
