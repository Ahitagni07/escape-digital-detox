import 'package:flutter/material.dart';
import 'features/escape/escape_controller.dart';
import 'features/setup/setup_screen.dart';
import 'features/dashboard/dashboard_screen.dart';

class EscapeApp extends StatefulWidget {
  const EscapeApp({super.key});

  @override
  State<EscapeApp> createState() => _EscapeAppState();
}

class _EscapeAppState extends State<EscapeApp> {
  late final EscapeController _controller;

  @override
  void initState() {
    super.initState();
    _controller = EscapeController()..initialize();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'ESCAPE',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF4F7A55),
        ),
        useMaterial3: true,
      ),
      home: AnimatedBuilder(
        animation: _controller,
        builder: (context, _) {
          if (_controller.loading) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }

          if (_controller.showSetup) {
            return SetupScreen(controller: _controller);
          }

          return DashboardScreen(controller: _controller);
        },
      ),
    );
  }
}
