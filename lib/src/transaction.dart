import 'package:universal_io/io.dart' as io;
import 'package:upi_pay/src/method_channel.dart';
import 'package:upi_pay/src/response.dart';
import 'package:upi_pay/src/transaction_details.dart';

class UpiTransactionHelper implements _PlatformTransactionHelperBase {
  final helper = io.Platform.isAndroid
      ? AndroidTransactionHelper()
      : io.Platform.isIOS
          ? IosTransactionHelper()
          : null;
  static final _singleton = UpiTransactionHelper._inner();
  factory UpiTransactionHelper() {
    return _singleton;
  }
  UpiTransactionHelper._inner();

  @override
  Future<UpiTransactionResponse> transact(UpiMethodChannel upiMethodChannel,
      TransactionDetails transactionDetails) async {
    if (io.Platform.isAndroid || io.Platform.isIOS) {
      return await helper!.transact(upiMethodChannel, transactionDetails);
    }
    throw UnsupportedError(
        'UPI transaction is available only on Android and iOS');
  }
}

class AndroidTransactionHelper implements _PlatformTransactionHelperBase {
  static final _singleton = AndroidTransactionHelper._inner();
  factory AndroidTransactionHelper() {
    return _singleton;
  }
  AndroidTransactionHelper._inner();

  @override
  Future<UpiTransactionResponse> transact(UpiMethodChannel upiMethodChannel,
      TransactionDetails transactionDetails) async {
    print("Initiating transaction on Android");
    String? responseString =
        await upiMethodChannel.initiateTransaction(transactionDetails);
    print("Response received $responseString");
    return UpiTransactionResponse.android(
        responseString == null ? "" : responseString);
  }
}

class IosTransactionHelper implements _PlatformTransactionHelperBase {
  static final _singleton = IosTransactionHelper._inner();
  factory IosTransactionHelper() {
    return _singleton;
  }
  IosTransactionHelper._inner();

  @override
  Future<UpiTransactionResponse> transact(UpiMethodChannel upiMethodChannel,
      TransactionDetails transactionDetails) async {
    try {
      final bool? result = await upiMethodChannel.launch(transactionDetails);
      return UpiTransactionResponse.ios(result != null ? result : false);
    } catch (error, stack) {
      print('iOS UPI app launch failure: $error');
      print('iOS UPI app launch failure stack: $stack');
      return UpiTransactionResponse.iosError(error);
    }
  }
}

abstract class _PlatformTransactionHelperBase {
  Future<UpiTransactionResponse> transact(
      UpiMethodChannel upiMethodChannel, TransactionDetails transactionDetails);
}
