import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_callkit_incoming/entities/entities.dart';
import 'package:flutter_callkit_incoming/flutter_callkit_incoming.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const MethodChannel channel = MethodChannel('flutter_callkit_incoming');
  const events = MethodChannel('flutter_callkit_incoming_events');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    messenger.setMockMethodCallHandler(channel, (methodCall) async => '42');
    messenger.setMockMethodCallHandler(events, (_) async => null);
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
    messenger.setMockMethodCallHandler(events, null);
  });

  test('audio routing waits for the native result and sends the call id',
      () async {
    final confirmed = Completer<bool>();
    MethodCall? command;
    messenger.setMockMethodCallHandler(channel, (call) async {
      command = call;
      return confirmed.future;
    });
    var completed = false;
    final request = FlutterCallkitIncoming.setAudioRoute(
      'call-1',
      isSpeakerEnabled: true,
      force: true,
    ).then((speaker) {
      completed = true;
      return speaker;
    });
    await pumpEventQueue();
    expect(completed, isFalse);
    expect(command?.method, 'setAudioRoute');
    expect(command?.arguments, {
      'id': 'call-1',
      'isSpeakerEnabled': true,
      'force': true,
    });
    confirmed.complete(true);
    expect(await request, isTrue);
  });

  test('automatic routing can confirm a headset instead of the speaker',
      () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.arguments['force'], isFalse);
      return false;
    });
    expect(
      await FlutterCallkitIncoming.setAudioRoute('call-1',
          isSpeakerEnabled: true),
      isFalse,
    );
  });

  test('native failure is not converted to success', () async {
    messenger.setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(code: 'CALL_AUDIO_ROUTE_FAILED');
    });
    await expectLater(
      FlutterCallkitIncoming.setAudioRoute('call-1', isSpeakerEnabled: true),
      throwsA(isA<PlatformException>()),
    );
  });

  test('missing confirmation is not converted to success', () async {
    messenger.setMockMethodCallHandler(channel, (_) async => null);
    await expectLater(
      FlutterCallkitIncoming.setAudioRoute('call-1', isSpeakerEnabled: true),
      throwsFormatException,
    );
  });

  test('audio route events retain their call identity and actual output',
      () async {
    final received = <CallEvent?>[];
    final sub = FlutterCallkitIncoming.onEvent.listen(received.add);
    await pumpEventQueue();
    await messenger.handlePlatformMessage(
      events.name,
      const StandardMethodCodec().encodeSuccessEnvelope({
        'event': CallEventConstants.actionCallAudioStateChange,
        'body': {'id': 'call-1', 'isSpeakerEnabled': true},
      }),
      (_) {},
    );
    await pumpEventQueue();
    expect(
      received.single,
      isA<CallEventActionCallAudioStateChange>()
          .having((event) => event.id, 'call id', 'call-1')
          .having((event) => event.isSpeakerEnabled, 'speaker', isTrue),
    );
    await sub.cancel();
  });
}
