import 'package:flutter/material.dart';
import '../../escape/escape_controller.dart';

class RulesCard extends StatelessWidget {
  final EscapeController controller;

  const RulesCard({
    super.key,
    required this.controller,
  });

  int _safeValue(int current, List<int> allowed, int fallback) {
    return allowed.contains(current) ? current : fallback;
  }

  @override
  Widget build(BuildContext context) {
    const missionDurations = <int>[5, 10, 15, 20, 30];
    const stepTargets = <int>[300, 600, 800, 1000, 1500];
    const accessDurations = <int>[15, 30, 45, 60, 90];

    final safeWalkMinutes =
        _safeValue(controller.walkMinutes, missionDurations, 10);
    final safeMinSteps =
        _safeValue(controller.minSteps, stepTargets, 600);
    final safeAccessMinutes =
        _safeValue(controller.accessMinutes, accessDurations, 45);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Rules', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  value: controller.demoMode,
                  title: const Text('Demo mode'),
                  subtitle: const Text(
                    '1 min mission → 2 min social access; reminders every minute',
                  ),
                  onChanged: controller.serviceRunning
                      ? null
                      : controller.setDemoMode,
                ),
                const Divider(),
                DropdownButtonFormField<int>(
                  value: safeWalkMinutes,
                  decoration: const InputDecoration(
                    labelText: 'Mission duration',
                  ),
                  items: missionDurations
                      .map(
                        (value) => DropdownMenuItem(
                          value: value,
                          child: Text('$value minutes'),
                        ),
                      )
                      .toList(),
                  onChanged: controller.serviceRunning || controller.demoMode
                      ? null
                      : (value) {
                          if (value != null) controller.setWalkMinutes(value);
                        },
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  value: safeMinSteps,
                  decoration: const InputDecoration(
                    labelText: 'Outdoor minimum steps',
                  ),
                  items: stepTargets
                      .map(
                        (value) => DropdownMenuItem(
                          value: value,
                          child: Text('$value steps'),
                        ),
                      )
                      .toList(),
                  onChanged: controller.serviceRunning || controller.demoMode
                      ? null
                      : (value) {
                          if (value != null) controller.setMinSteps(value);
                        },
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  value: safeAccessMinutes,
                  decoration: const InputDecoration(
                    labelText: 'Social access earned per mission',
                  ),
                  items: accessDurations
                      .map(
                        (value) => DropdownMenuItem(
                          value: value,
                          child: Text('$value minutes'),
                        ),
                      )
                      .toList(),
                  onChanged: controller.serviceRunning || controller.demoMode
                      ? null
                      : (value) {
                          if (value != null) controller.setAccessMinutes(value);
                        },
                ),
                const SizedBox(height: 12),
                const Text(
                  'Before 18:00, missions are outdoor/fresh-air activities. '
                  'After 18:00, Gemma switches to indoor screen-free ideas '
                  'such as reading or family time.',
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
