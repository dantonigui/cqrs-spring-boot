package com.project.cqrs.shared.event.user;

public final class UserLogoutEvent extends UserEvent {

    private UserLogoutEvent(Long userId) {
        super(userId);
    }

    private UserLogoutEvent() {}

    public static UserLogoutEvent logoutEvent(Long userId) {
        return new UserLogoutEvent(userId);
    }
}
