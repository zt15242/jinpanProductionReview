package other.xsy.salestarget.pojo;

import java.io.Serializable;

public class PickOption implements Serializable {

    private Boolean isActive;

    private String optionLabel;

    private Integer optionCode;

    private String optionApiKey;
    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public void setOptionLabel(String optionLabel) {
        this.optionLabel = optionLabel;
    }

    public Integer getOptionCode() {
        return optionCode;
    }

    public void setOptionCode(Integer optionCode) {
        this.optionCode = optionCode;
    }

    public String getOptionApiKey() {
        return optionApiKey;
    }

    public void setOptionApiKey(String optionApiKey) {
        this.optionApiKey = optionApiKey;
    }

    @Override
    public String toString() {
        return "PickOption{" +
                "isActive=" + isActive +
                ", optionLabel='" + optionLabel + '\'' +
                ", optionCode=" + optionCode +
                ", optionApiKey='" + optionApiKey + '\'' +
                '}';
    }
}