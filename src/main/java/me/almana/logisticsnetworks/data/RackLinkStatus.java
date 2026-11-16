package me.almana.logisticsnetworks.data;

public enum RackLinkStatus {
    OK,
    LEFT_EMPTY,
    RIGHT_EMPTY,
    NO_SHARED_TYPE,
    NO_FLOW;

    // One bit per ChannelType
    public static RackLinkStatus of(int leftExport, int leftImport, int rightExport, int rightImport) {
        int left = leftExport | leftImport;
        int right = rightExport | rightImport;
        if (left == 0) {
            return LEFT_EMPTY;
        }
        if (right == 0) {
            return RIGHT_EMPTY;
        }
        if ((left & right) == 0) {
            return NO_SHARED_TYPE;
        }
        if ((leftExport & rightImport) == 0 && (rightExport & leftImport) == 0) {
            return NO_FLOW;
        }
        return OK;
    }
}
