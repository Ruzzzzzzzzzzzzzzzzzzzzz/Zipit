package io.zipit.core.api;

import io.zipit.core.model.ClassPool;

/**
 * Injects runtime helpers into the program being protected.
 *
 * <p>Implementations may add classes (for example a string decryptor), embed
 * watermarks, or register manifest attributes.</p>
 */
public interface RuntimeSupport {

    /**
     * Ensures a string decryptor class exists in the pool.
     *
     * @param pool program pool
     * @param seed deterministic seed
     * @return internal name of the decryptor class
     */
    String ensureStringDecryptor(ClassPool pool, long seed);

    /**
     * Ensures an invokedynamic bootstrap helper exists in the pool.
     *
     * @param pool program pool
     * @param seed deterministic seed
     * @return internal name of the bootstrap class
     */
    String ensureIndyBootstrap(ClassPool pool, long seed);

    /**
     * Ensures the bytecode interpreter used by virtualized methods exists.
     *
     * @param pool program pool
     * @param seed deterministic seed
     * @return internal name of the interpreter class
     */
    String ensureVirtualMachine(ClassPool pool, long seed);

    /**
     * Embeds a watermark in the program.
     *
     * @param pool       program pool
     * @param watermark  watermark payload
     * @return description of where the mark was written, or {@code null}
     */
    String embedWatermark(ClassPool pool, String watermark);
}
