package io.github.rkaushikdev.swagebay.welding;

/**
 * A welder and a process, which is what continuity is kept against.
 *
 * @param welder the welder's stamp
 * @param process the process
 */
public record WelderProcess(String welder, WeldingProcess process) {}
