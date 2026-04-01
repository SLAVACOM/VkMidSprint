box.cfg{
    listen = 3301,
    memtx_memory = 256 * 1024 * 1024,
    wal_mode = 'none'
}

if not box.space.kv then
    local s = box.schema.space.create('kv')
    s:format({
        {name='key', type='string'},
        {name='value', type='string'}
    })
    s:create_index('primary', {type='hash', parts={'key'}})
    s:create_index('range_idx', {type='tree', parts={'key'}})
end

local username = "appuser"
local password = "apppass"

if not box.schema.user.exists(username) then
    box.schema.user.create(username, {password = password})
    box.schema.user.grant(username, "read,write,execute", "universe")
end

