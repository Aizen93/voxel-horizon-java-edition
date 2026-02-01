# Voxel Horizon Wiki Documentation

This directory contains comprehensive documentation for the Voxel Horizon Java Edition project.

## Documentation Structure

### Core Documentation

1. **[Home.md](Home.md)** - Main wiki landing page with project overview
2. **[Architecture.md](Architecture.md)** - System architecture, modules, and design principles
3. **[Interfaces.md](Interfaces.md)** - Core interfaces and their contracts
4. **[Implementation.md](Implementation.md)** - Detailed implementation guidance

### User Guides

5. **[Getting-Started.md](Getting-Started.md)** - Build, run, and basic usage
6. **[Gameplay-Features.md](Gameplay-Features.md)** - Current features and mechanics
7. **[Configuration-Guide.md](Configuration-Guide.md)** - Customizing world generation and biomes

### Reference

8. **[Technical-Reference.md](Technical-Reference.md)** - Data structures, constants, and specifications
9. **[Work-In-Progress.md](Work-In-Progress.md)** - Development status and roadmap

### Contributing

10. **[Contributing.md](Contributing.md)** - Guidelines for contributors

## Quick Navigation

**New to the project?** Start here:
1. [Home](Home.md) - Project overview
2. [Getting Started](Getting-Started.md) - Build and run
3. [Gameplay Features](Gameplay-Features.md) - What you can do

**Developers?** Read these:
1. [Architecture](Architecture.md) - System design
2. [Interfaces](Interfaces.md) - Core APIs
3. [Implementation](Implementation.md) - How it works
4. [Contributing](Contributing.md) - How to help

**Want to customize?**
1. [Configuration Guide](Configuration-Guide.md) - JSON-based customization
2. [Technical Reference](Technical-Reference.md) - Constants and IDs

## Documentation Standards

All documentation follows these principles:

- **Clear structure** - Logical sections with table of contents
- **Code examples** - Real, working code snippets
- **Diagrams** - Visual representations where helpful
- **Cross-references** - Links between related documents
- **Up-to-date** - Synchronized with code changes

## Contributing to Documentation

Found an error? Want to improve the docs?

1. Edit the relevant `.md` file
2. Submit a pull request
3. Follow the writing style of existing docs

See [Contributing.md](Contributing.md) for detailed guidelines.

## Building a PDF (Optional)

You can convert these markdown files to PDF using tools like:

```bash
# Using pandoc (if installed)
pandoc Home.md -o VoxelHorizon-Wiki.pdf

# Or combine all docs
pandoc Home.md Architecture.md Interfaces.md Implementation.md \
       Gameplay-Features.md Getting-Started.md Configuration-Guide.md \
       Technical-Reference.md Work-In-Progress.md Contributing.md \
       -o VoxelHorizon-Complete-Documentation.pdf
```

## Viewing Documentation

**Recommended**: Use a Markdown viewer or IDE with Markdown support:
- **GitHub**: Browse on GitHub for best rendering
- **VS Code**: Markdown preview (Ctrl+Shift+V)
- **IntelliJ IDEA**: Built-in Markdown support
- **Typora**: Dedicated Markdown editor

## Last Updated

**Date**: 2026-01-26  
**Version**: Terrain-Generation-Configurable Branch  
**Total Pages**: 10 documents

---

**Start Reading**: [Home →](Home.md)
