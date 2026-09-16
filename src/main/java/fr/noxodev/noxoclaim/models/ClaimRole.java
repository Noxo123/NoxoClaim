package fr.noxodev.noxoclaim.models;

public enum ClaimRole {
    OWNER(true, true, true, true),
    ADMIN(true, true, true, true),
    MEMBER(true, true, true, true),
    BUILDER(true, true, false, false),
    VISITOR(false, false, false, false);

    private final boolean build;
    private final boolean interact;
    private final boolean manage;
    private final boolean bypassFlags;

    ClaimRole(boolean build, boolean interact, boolean manage, boolean bypassFlags) {
        this.build = build;
        this.interact = interact;
        this.manage = manage;
        this.bypassFlags = bypassFlags;
    }

    public boolean canBuild() { return build; }
    public boolean canInteract() { return interact; }
    public boolean canManage() { return manage; }
    public boolean bypassFlags() { return bypassFlags; }
}
