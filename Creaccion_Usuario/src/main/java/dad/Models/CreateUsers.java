package dad.Models;

import com.fasterxml.jackson.annotation.*;

public class CreateUsers {

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;

    @JsonProperty("password_disabled")
    private Boolean passwordDisabled;

    @JsonProperty("group_create")
    private Boolean groupCreate;

    @JsonProperty("group")
    private Integer group;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("home")
    private String home;

    @JsonProperty("home_mode")
    private String homeMode;

    @JsonProperty("shell")
    private String shell;

    @JsonProperty("smb")
    private Boolean smb;

    public CreateUsers() {
    }

    public CreateUsers(String username, String password, Boolean passwordDisabled, Boolean groupCreate, Integer group, String fullName, String home, String homeMode, String shell, Boolean smb) {
        this.username = username;
        this.password = password;
        this.passwordDisabled = passwordDisabled;
        this.groupCreate = groupCreate;
        this.group = group;
        this.fullName = fullName;
        this.home = home;
        this.homeMode = homeMode;
        this.shell = shell;
        this.smb = smb;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getPasswordDisabled() {
        return passwordDisabled;
    }

    public void setPasswordDisabled(Boolean passwordDisabled) {
        this.passwordDisabled = passwordDisabled;
    }

    public Boolean getGroupCreate() {
        return groupCreate;
    }

    public void setGroupCreate(Boolean groupCreate) {
        this.groupCreate = groupCreate;
    }

    public Integer getGroup() {
        return group;
    }

    public void setGroup(Integer group) {
        this.group = group;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getHomeMode() {
        return homeMode;
    }

    public void setHomeMode(String homeMode) {
        this.homeMode = homeMode;
    }

    public String getShell() {
        return shell;
    }

    public void setShell(String shell) {
        this.shell = shell;
    }

    public Boolean getSmb() {
        return smb;
    }

    public void setSmb(Boolean smb) {
        this.smb = smb;
    }

    @Override
    public String toString() {
        return "CreateUsersController{" +
                "username='" + username + '\'' +
                ", password='" + (password != null ? "[PROTEGIDO]" : "null") + '\'' +
                ", passwordDisabled=" + passwordDisabled +
                ", groupCreate=" + groupCreate +
                ", group=" + group +
                ", fullName='" + fullName + '\'' +
                ", home='" + home + '\'' +
                ", homeMode='" + homeMode + '\'' +
                ", shell='" + shell + '\'' +
                ", smb=" + smb +
                '}';
    }
}