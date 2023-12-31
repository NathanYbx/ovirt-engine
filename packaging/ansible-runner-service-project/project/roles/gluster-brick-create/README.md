glusterfs-brick-create
=========

Given a set of physical devices, this role creates a volume group, a thin pool and thinly provisioned logical volume, creates an xfs filesystem and mounts the LV at a given path for use as a glusterfs brick. If an SSD device is provided, a lvmcache pool is created and attached to thin pool created.


Role Variables
--------------

| Parameters        | Required | Default | Choices  | Description |
| ----------------- | -------- | ------- | --------------------------------  | ----------- |
| disks             | yes      |         |          | List of physical devices on server. For example /dev/sdc
| disktype          | yes      |         | raid10, raid6, raid5, raid0, jbod | Type of the disk configuration
| diskcount         | no       | 1       |          | Number of data disks in RAID configuration. Required only in case of RAID disk type.
| stripesize        | no       | 256     |          | Stripe size configured at RAID controller. Value should be in KB. Required only in case of RAID disk type.
| vgname            | yes      |         |          | Name of the volume group that the disk is added to. The Volume Group will be created if not already present
| size              | yes      |         |          | Size of thinpool to be created on the volume group. Size should contain the units. For example, 100G
| lvname            | yes      |         |          | Name of the Logical volume created using the physical disk(s).
| ssd               | yes      |         |          | Name of the ssd device.
| cache_lvname      | yes      |         |          | Name of the Logical Volume to be used for cache.
| cache_lvsize      | yes      |         |          | Size of the cache logical volume
| mntpath           | yes      |         |          | Path to mount the filesystem.
| pool_metadatasize | yes      |         |          | Size of the pools metadata, should be between 2M to 16G (Example: 24M).
| wipefs            | no       | yes     | yes/no   | Whether to wipe the filesystem labels if present.
| fstype            | no       | xfs     |          | Type of filesystem to create.



Example Playbook to call the role
---------------------------------

```yaml
- hosts: servers
  remote_user: root
  vars:
    disks:
      - /dev/sdb
    disktype: none
    vgname: test_vg
    size: 3G
    lvname: lv_name
    ssd:  /dev/sdc
    cache_lvname: cache
    cache_lvsize: 100M
    mntpath: /root/mount_test
    pool_metadatasize: 24M
  roles:
    - glusterfs-brick-create
```

License
-------

Apache License 2.0
