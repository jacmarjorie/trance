import React from "react";
import { TransitionProps } from '@material-ui/core/transitions';
import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import ListItemText from '@material-ui/core/ListItemText';
import ListItem from '@material-ui/core/ListItem';
import List from '@material-ui/core/List';
import Divider from '@material-ui/core/Divider';
import AppBar from '@material-ui/core/AppBar';
import Toolbar from '@material-ui/core/Toolbar';
import IconButton from '@material-ui/core/IconButton';
import Typography from '@material-ui/core/Typography';
import CloseIcon from '@material-ui/icons/Close';
import Slide from '@material-ui/core/Slide';

import {shreddedVStandardThemeStyle} from './ShreddedVStandardThemeStyle';
import {Grid, Paper} from "@material-ui/core";
import SimpleBarGraphVShredded from "../../ui/Charts/SimpleBarChart/SimpleBarGraphVShredded";
import StarBorderIcon from "@material-ui/icons/StarBorder";
import {shred_part_dist} from '../../ui/Charts/SimpleBarChart/shred_part_dist';
import {stand_part_dist} from '../../ui/Charts/SimpleBarChart/stand_part_dist';




const Transition = React.forwardRef(function Transition(
    props: TransitionProps & { children?: React.ReactElement },
    ref: React.Ref<unknown>,
) {
    return <Slide direction="up" ref={ref} {...props} />;
});

interface _ShreddedVStandardProps{
    open: boolean;
    close:()=>void;
}

 const ShreddedVStandard = (props: _ShreddedVStandardProps) => {
     const classes = shreddedVStandardThemeStyle();
     return(
         <div>
             <Dialog fullScreen open={props.open} onClose={props.close} TransitionComponent={Transition}>
                 <AppBar className={classes.appBar}>
                     <Toolbar>
                         <IconButton edge="start" color="inherit" onClick={props.close} aria-label="close">
                             <CloseIcon />
                         </IconButton>
                         <Typography variant="h6" className={classes.title}>
                             Shredded vs Standard
                         </Typography>
                     </Toolbar>
                 </AppBar>
                    <Grid container spacing={2} >
                        <Grid item xs={12} >
                                <Paper className={classes.paper}>
                                    <Typography variant={"h6"}>Shredded Plan Metrics</Typography>
                                    <SimpleBarGraphVShredded data={shred_part_dist}/>
                                </Paper>
                        </Grid>
                        <Grid item xs={12} >
                            <Paper className={classes.paper}>
                                <Typography variant={"h6"}>Standard Plan Metrics</Typography>
                                <SimpleBarGraphVShredded data={stand_part_dist}/>
                            </Paper>
                        </Grid>
                    </Grid>
                 <Button className={classes.btn} variant={"contained"} style={{'backgroundColor':'#d66123'}} onClick={()=> window.location.href = "http://localhost:18080"} endIcon={<StarBorderIcon/>}>Metrics</Button>
             </Dialog>
         </div>
     );
}

export default ShreddedVStandard;