/**
 * World content configuration system for biome decoration rules.
 *
 * <p>This package provides a JSON-based configuration system for defining:
 * <ul>
 *   <li>Surface blocks per biome (top block, filler block)</li>
 *   <li>Tree placement rules (types, density, distribution)</li>
 *   <li>Vegetation placement rules</li>
 *   <li>Clustering and clearing configurations</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The configuration is loaded at class initialization by the existing generation classes:
 * <ul>
 *   <li>{@link org.aouessar.core.gen.impl.BiomeDecorator} - Loads surface block palettes from JSON</li>
 *   <li>{@link org.aouessar.core.gen.impl.DefaultStructureBuilder} - Loads tree and vegetation data from JSON</li>
 * </ul>
 *
 * <h2>Configuration Files</h2>
 * <p>Configuration files are located in {@code resources/constraints/} and follow
 * the naming pattern {@code world_content_v{version}.json}. This allows for
 * multiple versions to coexist and the system to adapt automatically.
 *
 * <h2>Tree Type Mapping</h2>
 * <p>Trees are special structures that require generation methods rather than
 * simple block IDs. The {@link org.aouessar.core.gen.config.TreeType} enum maps
 * JSON tree type names to structure marker IDs, which are then handled by
 * the ChunkBuilder's tree placement methods:
 * <ul>
 *   <li>OAK → placeOakTreePart()</li>
 *   <li>SPRUCE → placeSpruceTree()</li>
 *   <li>ACACIA → placeAcaciaTreePart()</li>
 *   <li>JUNGLE → placeJungleTreePart()</li>
 *   <li>MEGA_JUNGLE → placeMegaJunglePartNormal()</li>
 *   <li>SNOW_TREE → placeSnowTree()</li>
 *   <li>CACTUS → direct block placement</li>
 * </ul>
 *
 * <h2>Version Support</h2>
 * <p>When creating a new version of the configuration format:
 * <ol>
 *   <li>Create a new JSON file: {@code world_content_v{N}.json}</li>
 *   <li>Update {@code WorldContentLoader.LATEST_VERSION}</li>
 *   <li>Add any necessary parsing logic in {@code WorldContentLoader}</li>
 * </ol>
 *
 * @see org.aouessar.core.gen.config.WorldContentLoader
 * @see org.aouessar.core.gen.config.WorldContentConfig
 */
package org.aouessar.core.gen.config;
