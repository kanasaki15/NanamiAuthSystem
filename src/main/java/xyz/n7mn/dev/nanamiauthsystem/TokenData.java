package xyz.n7mn.dev.nanamiauthsystem;

import net.dv8tion.jda.api.entities.Message;

import java.util.Date;

public class TokenData {
    private String token;
    private Message message;
    private Date date;
    private String userId;

    public TokenData(String token, Message message, Date date, String userId){
        this.token = token;
        this.message = message;
        this.date = date;
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
