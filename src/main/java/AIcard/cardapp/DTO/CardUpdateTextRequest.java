package AIcard.cardapp.DTO;

import java.util.ArrayList;
import java.util.List;

public class CardUpdateTextRequest {

    private String displayName;
    private String jobTitle;
    private String company;
    private String department;
    private String intro;
    private String email;
    private String phone;
    private List<CardExtraItemRequest> extraItems = new ArrayList<>();

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<CardExtraItemRequest> getExtraItems() {
        return extraItems;
    }

    public void setExtraItems(List<CardExtraItemRequest> extraItems) {
        this.extraItems = extraItems == null ? new ArrayList<>() : extraItems;
    }
}
