package com.tss.aml.common.templete;

import java.util.List;

public class FileConstant {
    public static final List<String> REQUIRED_HEADERS = List.of(
            "TxnNo",
            "OriginatorAccountNo",
            "OriginatorName",
            "Amount",
            "Currency",
            "TxnType",
            "Direction",
            "CounterpartyName",
            "CounterpartyAccountNo",
            "CounterpartyBank",
            "CounterpartyCountryCode",
            "TxnTimestamp",
            "CountryCode"
    );

}
