/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2015-2018 the Valkyrien Warfare team
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income unless it is to be used as a part of a larger project (IE: "modpacks"), nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from the Valkyrien Warfare team.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: The Valkyrien Warfare team), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package valkyrienwarfare.addon.ftbutil.item;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.math.ChunkDimPos;
import com.feed_the_beast.ftbutilities.data.ClaimResult;
import com.feed_the_beast.ftbutilities.data.ClaimedChunks;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import valkyrienwarfare.ValkyrienWarfareMod;
import valkyrienwarfare.mod.physmanagement.chunk.VWChunkClaim;
import valkyrienwarfare.physics.management.PhysicsObject;
import valkyrienwarfare.physics.management.PhysicsWrapperEntity;

/**
 * Also contains hooks for claiming and unclaiming ships
 *
 * @author DaPorkchop_
 */
public class ItemAirshipClaimer extends Item {
    public void initialClaim(PhysicsObject object) {
        {
            GameProfile profile = object.getOwner();
            //unclaim all existing chunks (this shouldn't ever do anything, but you never know)
            this.handleUnclaim(object);
            object.setOwner(profile);
        }

        int dim = object.getWrapperEntity().dimension;
        ForgePlayer player = ClaimedChunks.instance.universe.getPlayer(object.getOwner());
        if (player == null) {
            throw new IllegalStateException("Unable to claim chunks for unknown player: " + object.getOwner().getName() + " (" + object.getOwner().getId() + ')');
        }
        Chunk[][] chunks2d = object.getClaimedChunks();
        boolean[][] occupied2d = object.getOwnedChunks().getChunkOccupiedInLocal();
        for (int x = chunks2d.length - 1; x >= 0; x--) {
            Chunk[] chunks1d = chunks2d[x];
            boolean[] occupied1d = occupied2d[x];
            for (int z = chunks1d.length - 1; z >= 0; z--) {
                if (occupied1d[z]) {
                    Chunk chunk = chunks1d[z];
                    ClaimResult result = ClaimedChunks.instance.claimChunk(player, new ChunkDimPos(chunk.x, chunk.z, dim));
                    if (result != ClaimResult.SUCCESS) {
                        this.handleUnclaim(object);
                        player.entityPlayer.sendMessage(new TextComponentString("Unable to claim chunks! Error at (" + chunk.x + ", " + chunk.z + "): " + result.name()));
                        return;
                    }
                }
            }
        }
    }

    public void handleClaim(PhysicsObject object, int relX, int relZ) {
        ForgePlayer player = ClaimedChunks.instance.universe.getPlayer(object.getOwner());
        if (player == null) {
            throw new IllegalStateException("Unable to claim chunks for unknown player: " + object.getOwner().getName() + " (" + object.getOwner().getId() + ')');
        }
        Chunk chunk = object.getClaimedChunks()[relX][relZ];
        ClaimResult result = ClaimedChunks.instance.claimChunk(player, new ChunkDimPos(chunk.x, chunk.z, object.getWrapperEntity().dimension));
        if (player.isOnline()) {
            player.entityPlayer.sendMessage(new TextComponentString("Unable to claim chunks! Error at (" + chunk.x + ", " + chunk.z + "): " + result.name()));
        }
    }

    public void handleUnclaim(PhysicsObject object) {
        int dim = object.getWrapperEntity().dimension;
        VWChunkClaim claim = object.getOwnedChunks();
        int minX = claim.getMinX();
        int minZ = claim.getMinZ();
        for (int x = claim.getMaxX(); x >= minX; x--) {
            for (int z = claim.getMaxZ(); z >= minZ; z--) {
                ClaimedChunks.instance.unclaimChunk(new ChunkDimPos(x, z, dim));
            }
        }
        object.setOwner(null);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        } else {
            PhysicsWrapperEntity entity = ValkyrienWarfareMod.VW_PHYSICS_MANAGER.getObjectManagingPos(world, pos);
            if (entity == null) {
                return EnumActionResult.FAIL;
            }
            PhysicsObject object = entity.getPhysicsObject();
            VWChunkClaim claim = object.getOwnedChunks();
            {
                ForgeTeam team = null;
                boolean unclaim = false;
                if (object.getOwner() != null) {
                    ForgePlayer currentOwner = ClaimedChunks.instance.universe.getPlayer(object.getOwner());
                    team = currentOwner.hasTeam() ? currentOwner.team : null;
                }
                if (team == null) {
                    ChunkDimPos dimPos = new ChunkDimPos(claim.getCenterX(), claim.getCenterZ(), player.dimension);
                    team = ClaimedChunks.instance.getChunkTeam(dimPos);
                }
                TEAM:
                if (team != null) {
                    if (this.isHighRankedMember(player.gameProfile, team)) {
                        unclaim = true;
                        break TEAM;
                    }
                    player.sendMessage(new TextComponentString("Ship already claimed by: ").appendSibling(team.getTitle()));
                    return EnumActionResult.FAIL;
                }

                if (unclaim) {
                    //unclaim ship
                    this.handleUnclaim(object);
                    object.setOwner(null);
                    player.sendMessage(new TextComponentString("Ship unclaimed!"));
                    return EnumActionResult.SUCCESS;
                }
            }

            object.setOwner(new GameProfile(player.gameProfile.getId(), player.gameProfile.getName()));
            this.initialClaim(object);
            player.sendMessage(new TextComponentString("Claimed ship successfully!"));
            return EnumActionResult.SUCCESS;
        }
    }

    private boolean isHighRankedMember(GameProfile profile, ForgeTeam team) {
        ForgePlayer player = ClaimedChunks.instance.universe.getPlayer(profile);
        switch (team.getHighestStatus(player)) {
            case MEMBER:
            case MOD:
            case OWNER:
                return true;
            default:
                return false;
        }
    }
}
