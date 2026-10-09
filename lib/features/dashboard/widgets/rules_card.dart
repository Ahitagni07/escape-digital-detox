import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class RulesCard extends StatelessWidget {
  final EscapeController controller;
  const RulesCard({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    const indoorMinutes = <int>[5, 10, 15, 20, 30];
    const accessOptions = <int>[15, 30, 45, 60, 90];
    const stepOptions = <int>[300, 600, 800, 1000, 1500];
    final steps = stepOptions.contains(controller.minSteps)
        ? controller.minSteps : 600;
    final indoor = indoorMinutes.contains(controller.walkMinutes)
        ? controller.walkMinutes : 10;
    final access = accessOptions.contains(controller.accessMinutes)
        ? controller.accessMinutes : 30;
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
              subtitle: const Text('1-minute activity + 30 steps (or 150 m ride) + photo → 2-minute reward. Reminders hourly.'),
              onChanged: controller.serviceRunning ? null : controller.setDemoMode,
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<int>(
              value: indoor,
              decoration: const InputDecoration(labelText: 'Quest duration (outdoor and indoor)'),
              items: indoorMinutes.map((x) => DropdownMenuItem(
                value: x, child: Text('$x minutes'))).toList(),
              onChanged: controller.serviceRunning || controller.demoMode
                  ? null : (v) { if (v != null) controller.setWalkMinutes(v); },
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<int>(
              value: steps,
              decoration: const InputDecoration(labelText: 'Outdoor walking step goal'),
              items: stepOptions.map((x) => DropdownMenuItem(
                value: x, child: Text('$x steps'))).toList(),
              onChanged: controller.serviceRunning || controller.demoMode
                  ? null : (v) { if (v != null) controller.setMinSteps(v); },
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
                'Day: walk or ride + find nature + fresh camera photo. '
                'After local sunset: nature-related paper challenge + photo proof.'),
          ],
        ),
      ),
    );
  }
}
