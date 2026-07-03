package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.nio.file.Path

class CompletionCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "completion") {
    override fun help(context: Context): String {
        return "Generate shell completion script"
    }

    private val shell by argument("SHELL", help = "Shell type: bash or zsh")
    private val verbose by option(
        "--verbose",
        "-v",
        help = "Show full stack traces on errors",
    ).flag(default = false)

    override fun run() {
        try {
            val script = when (shell.lowercase()) {
                "bash" -> generateBashCompletion()
                "zsh" -> generateZshCompletion()
                else -> {
                    echo("error: Unsupported shell. Use: bash or zsh", err = true)
                    throw ProgramResult(1)
                }
            }
            echo(script)
        } catch (ex: ProgramResult) {
            throw ex
        } catch (ex: Exception) {
            echo("error: ${ex.message ?: "Unknown error"}", err = true)
            if (verbose) {
                ex.printStackTrace(System.err)
            }
            throw ProgramResult(1)
        }
    }

    private fun generateBashCompletion(): String {
        return """
            # kli bash completion script
            # Installation: Add to ~/.bashrc or ~/.bash_profile:
            #   eval "${'$'}(kli completion bash)"
            
            _kli_completion() {
                local cur prev opts
                COMPREPLY=()
                cur="${'$'}{COMP_WORDS[COMP_CWORD]}"
                prev="${'$'}{COMP_WORDS[COMP_CWORD-1]}"
                
                # Get all commands and options
                opts="--version --help"
                local commands="init project-lint clean clean-all refresh run test build package publish dependency"
                
                # Main command completion
                if [[ ${'$'}{COMP_CWORD} -eq 1 ]]; then
                    COMPREPLY=( $(compgen -W "${'$'}{commands}" -- ${'$'}{cur}) )
                    return 0
                fi
                
                # Subcommand completion for dependency
                if [[ "${'$'}{COMP_WORDS[1]}" == "dependency" && ${'$'}{COMP_CWORD} -eq 2 ]]; then
                    local dep_cmds="list status add remove upgrade"
                    COMPREPLY=( $(compgen -W "${'$'}{dep_cmds}" -- ${'$'}{cur}) )
                    return 0
                fi
                
                # Option completion
                if [[ ${'$'}{cur} == -* ]]; then
                    COMPREPLY=( $(compgen -W "${'$'}{opts}" -- ${'$'}{cur}) )
                    return 0
                fi
                
                # File completion for paths
                COMPREPLY=( $(compgen -f -- ${'$'}{cur}) )
            }
            
            complete -o bashdefault -o default -o nospace -F _kli_completion kli
        """.trimIndent()
    }

    private fun generateZshCompletion(): String {
        return """
            # kli zsh completion script
            # Installation: Add to ~/.zshrc:
            #   eval "${'$'}(kli completion zsh)"
            
            _kli() {
                local -a commands=(
                    'init:Scaffold a new Kotlin project'
                    'project-lint:Validate project.json strictly'
                    'clean:Remove cache for current project'
                    'clean-all:Remove all project caches'
                    'refresh:Re-resolve dependencies and recompile'
                    'run:Compile and run a qualified main'
                    'test:Discover and run tests'
                    'build:Build a fat jar with dispatcher'
                    'package:Build and install jar to ~/.kli/m2'
                    'publish:Publish built package to registry'
                    'dependency:Manage project dependencies'
                )
                
                local -a dependency_cmds=(
                    'list:List all dependencies'
                    'status:Check for dependency updates'
                    'add:Add a dependency'
                    'remove:Remove a dependency'
                    'upgrade:Upgrade dependencies'
                )
                
                local -a global_opts=(
                    '--version:Display tool version'
                    '--help:Display help'
                    '-h:Display help'
                )
                
                local -a run_test_opts=(
                    '--show-compiler-logging:Show compiler diagnostics'
                    '--silent:Hide progress output'
                )
                
                local -a build_package_opts=(
                    '--output:Output file path'
                    '--show-compiler-logging:Show compiler diagnostics'
                    '--silent:Hide progress output'
                )
                
                local -a dependency_add_opts=(
                    '--scope:Dependency scope (runtime or test)'
                    '--latest:Resolve to latest version'
                    '--registry:Registry URL'
                    '--no-clean:Skip cache cleanup'
                )
                
                local -a dependency_remove_opts=(
                    '--scope:Dependency scope'
                    '--yes:Skip confirmation'
                    '-y:Skip confirmation'
                    '--no-clean:Skip cache cleanup'
                )
                
                local -a dependency_upgrade_opts=(
                    '--scope:Dependency scope'
                    '--yes:Skip confirmation'
                    '-y:Skip confirmation'
                    '--dry-run:Show changes only'
                    '--registry:Registry URL'
                    '--offline:Use only cached versions'
                    '--no-clean:Skip cache cleanup'
                )
                
                local -a publish_opts=(
                    '--registry:Registry URL'
                )
                
                case "${'$'}{CURRENT}" in
                    1)
                        _describe 'kli commands' commands
                        ;;
                    *)
                        case "${'$'}{words[2]}" in
                            dependency)
                                if [[ ${'$'}{CURRENT} -eq 2 ]]; then
                                    _describe 'dependency subcommands' dependency_cmds
                                else
                                    case "${'$'}{words[3]}" in
                                        add)
                                            _describe 'add options' dependency_add_opts
                                            ;;
                                        remove)
                                            _describe 'remove options' dependency_remove_opts
                                            ;;
                                        upgrade)
                                            _describe 'upgrade options' dependency_upgrade_opts
                                            ;;
                                    esac
                                fi
                                ;;
                            run|test)
                                _describe 'options' run_test_opts
                                _files
                                ;;
                            build|package)
                                _describe 'options' build_package_opts
                                ;;
                            publish)
                                _describe 'options' publish_opts
                                ;;
                        esac
                        ;;
                esac
            }
            
            compdef _kli kli
        """.trimIndent()
    }
}
