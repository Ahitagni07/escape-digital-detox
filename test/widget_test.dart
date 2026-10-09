import 'package:flutter_test/flutter_test.dart';

import 'package:escape/app.dart';

void main() {
  testWidgets('ESCAPE app builds', (WidgetTester tester) async {
    await tester.pumpWidget(const EscapeApp());
    await tester.pump();

    expect(find.byType(EscapeApp), findsOneWidget);
  });
}
