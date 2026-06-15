package dev.flaticols.applecontainer.cli

/** A `container` invocation failed: non-zero exit (carries stderr), a timeout, or a parse error. */
class ContainerCliException(message: String) : RuntimeException(message)
