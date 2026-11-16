package com.jarec.mesaj.copil.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UserInstanceLock implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(UserInstanceLock.class.getName());
    private final FileChannel channel;
    private final FileLock lock;
    private final Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean();

    private UserInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
        shutdownHook = new Thread(this::closeAtShutdown, "mesaj-user-instance-lock");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public static Optional<UserInstanceLock> tryAcquire(String username) throws IOException {
        return tryAcquire(Path.of(System.getProperty("user.home"), ".mesaj", "locks"), username);
    }

    public static Optional<UserInstanceLock> tryAcquire(Path lockDirectory, String username) throws IOException {
        if (lockDirectory == null || username == null || username.isBlank()) {
            throw new IllegalArgumentException("lockDirectory and a non-blank username are required");
        }
        Files.createDirectories(lockDirectory);
        LocalFilePermissions.restrict(lockDirectory, true);
        Path lockFile = lockDirectory.resolve(lockFileName(username));
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            LocalFilePermissions.restrict(lockFile, false);
            FileLock lock = tryLock(channel);
            if (lock == null) {
                channel.close();
                return Optional.empty();
            }
            return Optional.of(new UserInstanceLock(channel, lock));
        } catch (IOException | RuntimeException exception) {
            channel.close();
            throw exception;
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException _) {
            return null;
        }
    }

    private static String lockFileName(String username) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(username.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest) + ".lock";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void closeAtShutdown() {
        try {
            close();
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Failed to release Copil user lock during shutdown", exception);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException _) {
            // The JVM is already shutting down; the registered hook will finish cleanup.
        }

        IOException closeFailure = null;
        try {
            lock.release();
        } catch (IOException exception) {
            closeFailure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (closeFailure == null) {
                closeFailure = exception;
            } else {
                closeFailure.addSuppressed(exception);
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}
