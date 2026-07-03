package com.norman.swp391.entity.enums;

/**
 * Phan loai tiem nang cua mot du bao hot topic.
 *
 * <p>Luu vao DB va tra ve FE duoi dang MA on dinh (ASCII), tranh phu thuoc
 * chuoi tieng Viet — von de hong dau neu cot khong phai Unicode. FE tu map
 * ma -> nhan hien thi + mau badge.
 */
public enum ForecastCategory {
    /** sTPS >= 80 va dang tang toc -> "Bung no som". */
    EARLY_BOOM,
    /** sTPS >= 60 -> "Tang truong vuot bac". */
    BREAKOUT,
    /** Con lai -> "Tang truong on dinh". */
    STEADY;

    /**
     * Phan loai dua tren diem tiem nang (sTPS) va gia toc tho.
     *
     * @param potentialScore diem sTPS 0-100
     * @param acceleration   gia toc (slope nua gan day - slope nua truoc)
     */
    public static ForecastCategory classify(double potentialScore, double acceleration) {
        if (potentialScore >= 80 && acceleration > 0) return EARLY_BOOM;
        if (potentialScore >= 60) return BREAKOUT;
        return STEADY;
    }
}
