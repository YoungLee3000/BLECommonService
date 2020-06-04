package com.nlscan.blecommservice.utils;

/**
 *
 0 ZASETUP
 1 SETUP128
 2 CODE128
 3 UCCEAN128
 4 AIM128
 5 GS1_128
 6 ISBT128
 7 EAN8
 8 EAN13
 9 UPCE
 10 UPCA
 11 ISBN
 12 ISSN
 13 CODE39
 14 CODE93
 15 93I
 16 CODABAR
 17 ITF
 18 ITF6
 19 ITF14
 20 DPLEITCODE
 21 DPIDENTCODE
 22 CHNPOST25
 23 STANDARD25
 23 IATA25
 24 MATRIX25
 25 INDUSTRIAL25
 26 COOP25
 27 CODE11
 28 MSIPLESSEY
 29 PLESSEY
 30 RSS14
 31 RSSLIMITED
 32 RSSEXPANDED
 33 TELEPEN
 34 CHANNELCODE
 35 CODE32
 36 CODEZ

 37 CODABLOCKF
 38 CODABLOCKA
 39 CODE49
 40 CODE16K
 41 HIBC128
 42 HIBC39
 43 RSSFAMILY
 44 TriopticCODE39
 45 UPC_E1
 256 PDF417
 257 MICROPDF
 258 QRCODE
 259 MICROQR
 260 AZTEC
 261 DATAMATRIX
 262 MAXICODE
 263 CSCODE
 264 GRIDMATRIX
 265 EARMARK
 266 VERICODE
 267 CCA
 268 CCB
 269 CCC
 270 COMPOSITE
 271 HIBCAZT
 272 HIBCDM
 273 HIBCMICROPDF
 274 HIBCQR
 512 POSTNET
 513 ONECODE
 514 RM4SCC
 515 PLANET
 516 KIX
 517 APCUSTOM
 518 APREDIRECT
 519 APREPLYPAID
 520 APROUTING
 768 NUMOCRB
 769 PASSPORT
 770 TD1
 2048 PRIVATE
 2049 ZZCODE
 65535 UNKNOWN
 */
public class CommonCodeType {
    public static final int ZASETUP = 0;
   public static final int SETUP128 =1;
   public static final int CODE128 = 2;
   public static final int UCCEAN128 =3;
   public static final int AIM128 = 4;
   public static final  int GS1_128 = 5;
   public static final  int ISBT128 = 6;
   public static final  int EAN8 = 7;
   public static  final int EAN13 = 8;
   public static  final int UPCE = 9;
   public static final  int UPCA = 10;
   public static final  int ISBN = 11;
   public static final  int ISSN = 12;
   public static  final  int CODE39 = 13;
   public static   final int CODE93 = 14;
   public static  int _93I = 15;
   public static  final  int  CODABAR = 16;
   public static  int  ITF = 17;
   public static  final  int  ITF6 = 18;
   public static  final  int  ITF14 = 19;
   public static  int  DPLEITCODE = 20;
   public static  int  DPIDENTCODE = 21;
   public static  final  int  CHNPOST25 = 22;
   public static  final  int  STANDARD25 = 23;
   public static  int  IATA25 = 23;
   public static final   int  MATRIX25 = 24;
   public static  int  INDUSTRIAL25 = 25;
   public static  final  int  COOP25 = 26;
   public static  final  int  CODE11 = 27;
   public static  final  int  MSIPLESSEY = 28;
   public static  final  int  PLESSEY = 29;
   public static  int  RSS14 = 30;
   public static  int  RSSLIMITED = 31;
   public static  int  RSSEXPANDED = 32;
   public static  int  TELEPEN = 33;
   public static  int  CHANNELCODE = 34;
   public static  int  CODE32 = 35;
   public static  int  CODEZ = 36;

   public static  int  CODABLOCKF = 37;
   public static  int  CODABLOCKA = 38;
   public static  final  int  CODE49 = 39;
   public static  final  int  CODE16K = 40;
   public static  int  HIBC128 = 41;
   public static  int  HIBC39 = 42;
   public static  int  RSSFAMILY = 43;
   public static  int  TriopticCODE39 = 44;
   public static  int  UPC_E1 = 45;
   public static  final  int  PDF417 = 256;
   public static  final  int  MICROPDF = 257;
   public static  final  int  QRCODE = 258;
   public static  final  int  MICROQR = 259;
   public static  final  int  AZTEC = 260;
   public static  final  int  DATAMATRIX = 261;
   public static  final  int  MAXICODE = 262;
   public static  final  int  CSCODE = 263;


   public static   final int  GRIDMATRIX = 264;
   public static  int  EARMARK = 265;
   public static  int  VERICODE = 266;
   public static  int  CCA = 267;
   public static  int  CCB = 268;
   public static  int  CCC = 269;
   public static  int  COMPOSITE = 270;
   public static  int  HIBCAZT = 271;
   public static  int  HIBCDM = 272;
   public static  int  HIBCMICROPDF = 273;
   public static  int  HIBCQR = 274;
   public static  int  POSTNET = 512;
   public static   final int  ONECODE = 513;
   public static  int  RM4SCC = 514;
   public static  int  PLANET = 515;
   public static final   int  KIX = 516;
   public static  int  APCUSTOM = 517;
   public static  int  APREDIRECT = 518;
   public static  int  APREPLYPAID = 519;
   public static  int  APROUTING = 520;
   public static  int  NUMOCRB = 768;
   public static  int  PASSPORT = 769;
   public static  int  TD1 = 770;
   public static  int  PRIVATE = 2048;
   public static  int  ZZCODE = 2049;
   public static  int  UNKNOWN = 65535;

    public static int[] codeTypesCommon = {
            CODE128,  UCCEAN128,  EAN8,      EAN13,       UPCE,   UPCA /*, Interleaved_2_OF_5*/, ITF14, ITF6, MATRIX25,
            CODE39,   CODABAR,            CODE93,       CHNPOST25, AIM128 , ISBT128, COOP25,           ISSN,   ISBN,  /*,Industrial25,*/
            STANDARD25, PLESSEY ,       CODE11, MSIPLESSEY, /*GS1_Composite, GS1_Databar_RSS, */PDF417, QRCODE , AZTEC ,DATAMATRIX,
            MAXICODE, CSCODE, GRIDMATRIX ,MICROPDF, MICROQR , ONECODE, /*DotCode,*/ NUMOCRB, /*Passport_OCR,USPS_Postnet,*/
            /*USPS_Inteligent_Mail, Royal_Mail, USPS_Planet, Australian_Postal, Japan_Post,*/ CODE49, CODE16K,KIX
    };
}
