import 'package:flutter/material.dart';
import '../escape/escape_controller.dart';
import '../ai/ai_setup_screen.dart';
import 'widgets/ai_download_status_card.dart';
import 'widgets/emergency_unlock_card.dart';
import 'widgets/live_usage_card.dart';
import 'widgets/protected_apps_section.dart';
import 'widgets/recovery_mission_card.dart';
import 'widgets/rules_card.dart';
import 'widgets/stats_card.dart';
import 'widgets/status_hero.dart';

class DashboardScreen extends StatelessWidget {
  final EscapeController controller;

  const DashboardScreen({
    super.key,
    required this.controller,
  });

  void _showMessage(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ESCAPE 🌱'),
        actions: [
          IconButton(
            tooltip: 'Local AI / Gemma',
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const AiSetupScreen(),
                ),
              );
              await controller.refreshStatus();
            },
            icon: Icon(
              controller.aiModelInstalled
                  ? Icons.smart_toy
                  : Icons.smart_toy_outlined,
            ),
          ),
          IconButton(
            tooltip: 'Permission setup',
            onPressed: controller.openSetup,
            icon: const Icon(Icons.admin_panel_settings_outlined),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          StatusHero(controller: controller),
          const SizedBox(height: 16),

          AiDownloadStatusCard(
            controller: controller,
            onMessage: (message) => _showMessage(context, message),
          ),
          const SizedBox(height: 16),

          if (controller.locked) ...[
            RecoveryMissionCard(controller: controller),
            const SizedBox(height: 16),
          ] else if (controller.serviceRunning && controller.accessActive) ...[
            LiveUsageCard(controller: controller),
            const SizedBox(height: 16),
          ],

          EscapeStatsCard(controller: controller),
          const SizedBox(height: 20),

          ProtectedAppsSection(controller: controller),
          const SizedBox(height: 20),

          RulesCard(controller: controller),
          const SizedBox(height: 18),

          if (!controller.serviceRunning)
            FilledButton.icon(
              onPressed: () async {
                final message = await controller.startEscape();
                if (context.mounted && message != null) {
                  _showMessage(context, message);
                }
              },
              icon: const Icon(Icons.shield_outlined),
              label: const Padding(
                padding: EdgeInsets.symmetric(vertical: 14),
                child: Text('START ESCAPE'),
              ),
            )
          else ...[
            if (!controller.missionActive) ...[
              FilledButton.tonalIcon(
                onPressed: () async {
                  final message = await controller.triggerTestLock();
                  if (context.mounted) _showMessage(context, message);
                },
                icon: const Icon(Icons.bug_report_outlined),
                label: const Text('RESET TO MISSION — DEMO'),
              ),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: () async {
                  final message = await controller.stopEscape();
                  if (context.mounted) _showMessage(context, message);
                },
                icon: const Icon(Icons.pause),
                label: const Text('PAUSE ESCAPE'),
              ),
            ] else
              const Card(
                child: Padding(
                  padding: EdgeInsets.all(14),
                  child: Text(
                    'Finish the active mission or use Emergency Unlock before pausing ESCAPE.',
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
          ],

          if (controller.locked && controller.missionActive) ...[
            const SizedBox(height: 24),
            EmergencyUnlockCard(
              controller: controller,
              onUnlocked: (message) => _showMessage(context, message),
            ),
          ],
        ],
      ),
    );
  }
}
