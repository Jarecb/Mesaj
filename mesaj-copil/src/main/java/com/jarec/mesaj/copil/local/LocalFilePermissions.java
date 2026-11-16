package com.jarec.mesaj.copil.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class LocalFilePermissions {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);

    private LocalFilePermissions() {
    }

    static void restrict(Path path, boolean directory) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, directory ? DIRECTORY_PERMISSIONS : FILE_PERMISSIONS);
            return;
        }
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        FileOwnerAttributeView ownerView = Files.getFileAttributeView(path, FileOwnerAttributeView.class);
        if (aclView == null || ownerView == null) {
            throw new IOException("File system cannot restrict local Mesaj storage permissions");
        }
        AclEntry.Builder ownerEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(ownerView.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) {
            ownerEntry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        }
        aclView.setAcl(List.of(ownerEntry.build()));
    }
}
