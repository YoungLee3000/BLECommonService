package com.nlscan.blecommservice.utils;

/**
 *
 Code 128	002
 GS1-128 (UCC/EAN-128)	003
 EAN-8	004
 EAN-13	005
 UPC-E	006
 UPC-A	007
 Interleaved 2 OF 5	008
 ITF-14	009
 ITF-6	010
 Matrix 2 of 5	011
 Code 39	013
 Codabar	015
 Code 93	017
 China Post 25	019
 AIM 128	020
 ISBT 128	021
 COOP 25	022
 ISSN	023
 ISBN	024
 Industrial25	025
 Standard25	026
 Plessey	027
 Code11	028
 MSI-Plessey	029
 GS1 Composite	030
 GS1 Databar (RSS)	031
 PDF417	032
 QR Code	033
 Aztec	034
 Data Matrix	035
 Maxicode	036
 Chinese Sensible Code	039
 GM Code	040
 Micro PDF417	042
 Micro QR	043
 Code One	048
 DotCode	050
 Specific OCR-B	064
 Passport OCR	066
 USPS Postnet	096
 USPS Inteligent Mail	097
 Royal Mail	098
 USPS Planet	099
 KIX Post	100
 Australian Postal	101
 Japan Post	102
 Code 49	132
 Code 16K	133
 */
public class CodeType {

    public static final int Code_128 = 2;
    public static final int GS1_128_UCC_EAN_128 = 3;
    public static final int EAN_8 = 4;
    public static final int  EAN_13 = 5;
    public static final int UPC_E = 6;
    public static final int  UPC_A = 7;
    public static final int Interleaved_2_OF_5 = 8;
    public static final int ITF_14 = 9;
    public static final int ITF_6 = 10;
    public static final int Matrix_2_of_5 = 11;

    public static final int Code_39 = 13;
    public static final int Codabar = 15;
    public static final int Code_93 = 17;
    public static final int China_Post_25 = 19;
    public static final int AIM_128 = 20;
    public static final int ISBT_128 = 21;
    public static final int COOP_25 = 22;
    public static final int ISSN = 23;
    public static final int ISBN = 24;
    public static final int Industrial25 = 25;




    public static final int Standard25 = 26;
    public static final int Plessey = 27;
    public static final int Code11 = 28;
    public static final int MSI_Plessey = 29;
    public static final int GS1_Composite = 30;
    public static final int GS1_Databar_RSS = 31;
    public static final int PDF417 = 32;
    public static final int QR_Code = 33;
    public static final int Aztec = 34;
    public static final int Data_Matrix = 35;


    public static final int Maxicode = 36;
    public static final int Chinese_Sensible_Code = 39;
    public static final int GM_Code = 40;
    public static final int Micro_PDF417 = 42;
    public static final int Micro_QR = 43;
    public static final int Code_One = 48;
    public static final int DotCode = 50;
    public static final int Specific_OCR_B = 64;
    public static final int Passport_OCR = 66;
    public static final int USPS_Postnet = 96;

    public static final int USPS_Inteligent_Mail = 97;
    public static final int Royal_Mail = 98;
    public static final int USPS_Planet = 99;
    public static final int KIX_Post = 100;
    public static final int Australian_Postal = 101;
    public static final int Japan_Post = 102;
    public static final int Code_49 = 132;
    public static final int Code_16K = 133;

    private static int[] codeTypes = {
            Code_128,  GS1_128_UCC_EAN_128, EAN_8,         EAN_13,       UPC_E,   UPC_A /*,     Interleaved_2_OF_5*/, ITF_14, ITF_6, Matrix_2_of_5,
            Code_39,   Codabar,            Code_93,       China_Post_25, AIM_128 , ISBT_128, COOP_25,           ISSN,   ISBN,  /*Industrial25,*/
            Standard25, Plessey,        Code11, MSI_Plessey, /*GS1_Composite, GS1_Databar_RSS,*/PDF417, QR_Code , Aztec ,Data_Matrix,
            Maxicode, Chinese_Sensible_Code, GM_Code ,Micro_PDF417,Micro_QR , Code_One, /*DotCode, */Specific_OCR_B,/*Passport_OCR,USPS_Postnet,*/
            /*USPS_Inteligent_Mail,Royal_Mail,USPS_Planet,Australian_Postal,Japan_Post,*/Code_49,Code_16K,KIX_Post
    };


    public static int changeCodeTypeToCommon(int badgeCodeType){
        for (int i = 0; i < codeTypes.length; i++) {
            if (codeTypes[i] == badgeCodeType){
                return CommonCodeType.codeTypesCommon[i];
            }
        }
        return CommonCodeType.UNKNOWN;
    }

    public static int getCodeTypeFromCommon(int ommonCodetype){
        for (int i = 0; i < CommonCodeType.codeTypesCommon.length; i++) {
            if (CommonCodeType.codeTypesCommon[i] == ommonCodetype){
                return codeTypes[i];
            }
        }
        return 0;
    }
    public static String getCodeTypeString(int codeType){
        int code = getCodeTypeFromCommon(codeType);
        switch (code) {
            case Code_128: return "Code 128";
            case GS1_128_UCC_EAN_128: return "GS1-128 (UCC/EAN-128)";
            case EAN_8: return "EAN-8";
            case EAN_13: return "EAN-13";
            case UPC_E: return "UPC-E";
            case UPC_A: return "UPC-A";
            case Interleaved_2_OF_5: return "Interleaved 2 OF 5";
            case ITF_14: return "ITF-14";
            case ITF_6: return "ITF-6";
            case Matrix_2_of_5: return "Matrix 2 of 5";
            case Code_39: return "Code 39";
            case Codabar: return "Codabar";
            case Code_93: return "Code 93";
            case China_Post_25: return "China Post 25";
            case AIM_128: return "AIM 128";
            case ISBT_128: return "ISBT 128";
            case COOP_25: return "COOP_25";
            case ISSN: return "ISSN";
            case ISBN: return "ISBN";
            case Industrial25: return "Industrial25";
            case Standard25: return "Standard25";
            case Plessey: return "Plessey";
            case Code11: return "Code11";
            case MSI_Plessey: return "MSI_Plessey";
            case GS1_Composite: return "GS1 Composite";
            case GS1_Databar_RSS: return "GS1 Databar (RSS)";
            case PDF417: return "PDF417";
            case QR_Code: return "QR Code";
            case Aztec: return "Aztec";
            case Data_Matrix: return "Data_Matrix";
            case Maxicode: return "Maxicode";
            case Chinese_Sensible_Code: return "Chinese_Sensible_Code";
            case GM_Code: return "GM_Code";
            case Micro_PDF417: return "Micro_PDF417";
            case Micro_QR: return "Micro_QR";
            case Code_One: return "Code_One";
            case DotCode: return "DotCode";
            case Specific_OCR_B: return "Specific OCR-B";
            case Passport_OCR: return "Passport_OCR";
            case USPS_Postnet:return "USPS_Postnet";
            case USPS_Inteligent_Mail:return "USPS_Inteligent_Mail";
            case Royal_Mail:return "Royal_Mail";
            case USPS_Planet:return "USPS_Planet";
            case KIX_Post: return "KIX_Post";
            case Australian_Postal:return "Australian_Postal";
            case Japan_Post:return "Japan_Post";
            case Code_49: return "Code_49";
            case Code_16K: return "Code_16K";
        }
        return "UnKnow";
    }
}
